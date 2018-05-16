
/*
 * Copyright (c) 2018 Sony Pictures Imageworks Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package com.imageworks.spcue.dispatcher;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.log4j.Logger;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;

import com.imageworks.spcue.DispatchHost;
import com.imageworks.spcue.Frame;
import com.imageworks.spcue.JobEntity;
import com.imageworks.spcue.LayerEntity;
import com.imageworks.spcue.LocalHostAssignment;
import com.imageworks.spcue.Source;
import com.imageworks.spcue.VirtualProc;
import com.imageworks.spcue.CueIce.HardwareState;
import com.imageworks.spcue.CueIce.LockState;
import com.imageworks.spcue.RqdIce.BootReport;
import com.imageworks.spcue.RqdIce.HostReport;
import com.imageworks.spcue.RqdIce.RenderHost;
import com.imageworks.spcue.RqdIce.RunningFrameInfo;
import com.imageworks.spcue.dao.JobDao;
import com.imageworks.spcue.dao.LayerDao;
import com.imageworks.spcue.dispatcher.commands.DispatchBookHost;
import com.imageworks.spcue.dispatcher.commands.DispatchBookHostLocal;
import com.imageworks.spcue.dispatcher.commands.DispatchHandleHostReport;
import com.imageworks.spcue.dispatcher.commands.DispatchRqdKillFrame;
import com.imageworks.spcue.iceclient.RqdClient;
import com.imageworks.spcue.iceclient.RqdClientException;
import com.imageworks.spcue.service.BookingManager;
import com.imageworks.spcue.service.HostManager;
import com.imageworks.spcue.service.JobManager;
import com.imageworks.spcue.service.JobManagerSupport;
import com.imageworks.spcue.util.CueExceptionUtil;
import com.imageworks.spcue.util.CueUtil;

public class HostReportHandler {

    private static final Logger logger = Logger.getLogger(HostReportHandler.class);

    private BookingManager bookingManager;
    private HostManager hostManager;
    private BookingQueue bookingQueue;
    private ThreadPoolExecutor reportQueue;
    private ThreadPoolExecutor killQueue;
    private DispatchSupport dispatchSupport;
    private Dispatcher dispatcher;
    private Dispatcher localDispatcher;
    private RqdClient rqdClient;
    private JobManager jobManager;
    private JobManagerSupport jobManagerSupport;
    private JobDao jobDao;
    private LayerDao layerDao;

    /**
     * Boolean to toggle if this class is accepting data or not.
     */
    public boolean shutdown = false;

    /**
     * Return true if this handler is not accepting packets anymore.
     * @return
     */
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Shutdown this handler so it no longer accepts packets.  Any
     * call to queue a host report will throw an exception.
     */
    public synchronized void shutdown() {
        logger.info("Shutting down HostReportHandler.");
        shutdown = true;
    }

    /**
     * Queues up the given boot report.
     *
     * @param report
     */
    public void queueBootReport(BootReport report) {
        if (isShutdown()) {
            throw new RqdRetryReportException(
                    "Error processing host repport. Cuebot not " +
                    "accepting packets.");
        }
        reportQueue.execute(new DispatchHandleHostReport(report, this));
    }

    /**
     * Queues up the given host report.
     *
     * @param report
     */
    public void queueHostReport(HostReport report) {
        if (isShutdown()) {
            throw new RqdRetryReportException(
                    "Error processing host repport. Cuebot not " +
                    "accepting packets.");
        }
        reportQueue.execute(new DispatchHandleHostReport(report, this));
    }


    public void handleHostReport(HostReport report, boolean isBoot) {
        long startTime = System.currentTimeMillis();
        try {

            long totalGpu;
            if (report.host.attributes.containsKey("totalGpu"))
                totalGpu = Integer.parseInt(report.host.attributes.get("totalGpu"));
            else
                totalGpu = 0;

            long freeGpu;
            if (report.host.attributes.containsKey("freeGpu"))
                freeGpu = Integer.parseInt(report.host.attributes.get("freeGpu"));
            else
                freeGpu = 0;

            long swapOut = 0;
            if (report.host.attributes.containsKey("swapout")) {
                swapOut = Integer.parseInt(report.host.attributes.get("swapout"));
                if (swapOut > 0)
                    logger.info(report.host.name + " swapout: " +
                                report.host.attributes.get("swapout"));
            }

            DispatchHost host;
            RenderHost rhost = report.host;
            try {
                host = hostManager.findDispatchHost(rhost.name);
                hostManager.setHostStatistics(host,
                        rhost.totalMem, rhost.freeMem,
                        rhost.totalSwap, rhost.freeSwap,
                        rhost.totalMcp, rhost.freeMcp,
                        totalGpu, freeGpu,
                        rhost.load, new Timestamp(rhost.bootTime * 1000l),
                        rhost.attributes.get("SP_OS"));

                changeHardwareState(host, report.host.state);
                changeNimbyState(host, report.host);

                /**
                 * This should only happen at boot time or it will
                 * fight with the dispatcher over row locks.
                 */
                if (isBoot) {
                    hostManager.setHostResources(host, report);
                }

                dispatchSupport.determineIdleCores(host, report.host.load);

            } catch (DataAccessException dae) {
                logger.warn("Unable to find host " + rhost.name + ","
                        + dae + " , creating host.");
                // TODO: Skip adding it if the host name is over 30 characters

                host = hostManager.createHost(report);
            }
            catch (Exception e) {
                logger.warn("Error processing HostReport, " + e);
                return;
            }

            /*
             * Verify all the frames in the report are valid.
             * Frames that are not valid are removed.
             */
            verifyRunningFrameInfo(report);

            /*
             * Updates memory usage for the proc, frames,
             * jobs, and layers.
             */
            updateMemoryUsage(report.frames);

            /*
             * Increase/decreased reserved memory.
             */
            handleMemoryReservations(host, report);

            /*
             * The checks are done in order of least CPU intensive to
             * most CPU intensive, saving checks that hit the DB for last.
             *
             * These are done so we don't populate the booking queue with
             * a bunch of hosts that can't be booked.
             */
            String msg = null;
            boolean hasLocalJob = bookingManager.hasLocalHostAssignment(host);

            if (hasLocalJob) {
                List<LocalHostAssignment> lcas =
                    bookingManager.getLocalHostAssignment(host);
                for (LocalHostAssignment lca : lcas) {
                    bookingManager.removeInactiveLocalHostAssignment(lca);
                }
            }

            if (host.idleCores < Dispatcher.CORE_POINTS_RESERVED_MIN) {
                msg = String.format("%s doesn't have enough idle cores, %d needs %d",
                    host.name,  host.idleCores, Dispatcher.CORE_POINTS_RESERVED_MIN);
            }
            else if (host.idleMemory < Dispatcher.MEM_RESERVED_MIN) {
                msg = String.format("%s doesn't have enough idle memory, %d needs %d",
                        host.name,  host.idleMemory,  Dispatcher.MEM_RESERVED_MIN);
            }
            else if (report.host.freeMem < CueUtil.MB512) {
                msg = String.format("%s doens't have enough free system mem, %d needs %d",
                        host.name, report.host.freeMem,  Dispatcher.MEM_RESERVED_MIN);
            }
            else if(!host.hardwareState.equals(HardwareState.Up)) {
                msg = host + " is not in the Up state.";
            }
            else if (host.lockState.equals(LockState.Locked)) {
                msg = host + " is locked.";
            }
            else if (report.host.nimbyLocked) {
                if (!hasLocalJob) {
                    msg = host + " is NIMBY locked.";
                }
            }
            else if (!dispatchSupport.isCueBookable(host)) {
                msg = "The cue has no pending jobs";
            }

            /*
             * If a message was set, the hot is not bookable.  Log
             * the message and move on.
             */
            if (msg != null) {
                logger.trace(msg);
            }
            else {

                // check again. The dangling local host assignment could be removed.
                hasLocalJob = bookingManager.hasLocalHostAssignment(host);

                /*
                 * Check to see if a local job has been assigned.
                 */
                if (hasLocalJob) {
                    if (!bookingManager.hasResourceDeficit(host)) {
                        bookingQueue.execute(
                                new DispatchBookHostLocal(
                                        host, localDispatcher));
                    }
                    return;
                }

                /*
                 * Check for NIMBY blackout time.
                 */
                /*
                if (bookingManager.isBlackOutTime(host)) {
                    logger.trace(host + " is blacked out.");
                    return ;
                }
                */

                /*
                 * Check if the host prefers a show.  If it does , dispatch
                 * to that show first.
                 */
                if (hostManager.isPreferShow(host)) {
                    bookingQueue.execute(new DispatchBookHost(
                            host, hostManager.getPreferredShow(host), dispatcher));
                    return;
                }

                bookingQueue.execute(new DispatchBookHost(host, dispatcher));
            }

        } finally {
            if (reportQueue.getQueue().size() > 0 ||
                    System.currentTimeMillis() - startTime > 100) {
                /*
                 * Write a log if the host report takes a long time to process.
                 */
                CueUtil.logDuration(startTime, "host report " +
                        report.host.name + " with " +
                        report.frames.size() +
                        " running frames, waiting: " +
                        reportQueue.getQueue().size());
            }
        }
    }

    /**
     * Update the hardware state property.
     *
     * If a host pings in with a different hardware state than what
     * is currently in the DB, the state is updated.  If the hardware
     * state is Rebooting RebootWhenIdle, then state can only be
     * updated with a boot report.  If the state is Repair, then state is
     * never updated via RQD.
     *
     * @param host
     * @param reportState
     * @param isBoot
     */
    private void changeHardwareState(DispatchHost host,
            HardwareState reportState) {

        /*
         * If the states are the same there is no reason
         * to do this update.
         */
        if (host.hardwareState.equals(reportState)) {
            return;
        }

        /*
         * Do not change the state of the host if its in a
         * repair state.  Removing the repair state must
         * be done manually.
         */
        if (host.hardwareState.equals(HardwareState.Repair)) {
            return;
        }

        /*
         * Hosts in these states always change to Up.
         */
        if (reportState.equals(HardwareState.Up) && EnumSet.of(HardwareState.Down,
                HardwareState.Rebooting,
                HardwareState.RebootWhenIdle).contains(host.hardwareState)) {
            hostManager.setHostState(host, HardwareState.Up);
        }
        else {
            hostManager.setHostState(host, reportState);
        }

        host.hardwareState = reportState;
    }

    /**
     * Changes the NIMBY lock state.  If the DB indicates a NIMBY lock
     * but RQD does not, then the host is unlocked.  If the DB indicates
     * the host is not locked but RQD indicates it is, the host is locked.
     *
     * @param host
     * @param rh
     */
    private void changeNimbyState(DispatchHost host, RenderHost rh) {
        if (rh.nimbyLocked) {
            if (host.lockState.equals(LockState.Open)) {
                host.lockState = LockState.NimbyLocked;
                hostManager.setHostLock(host,LockState.NimbyLocked, new Source("NIMBY"));
            }
        }
        else {
            if (host.lockState.equals(LockState.NimbyLocked)) {
                host.lockState = LockState.Open;
                hostManager.setHostLock(host,LockState.Open, new Source("NIMBY"));
            }
        }
    }

    /**
     * Handle memory reservations for the given host.  This will re-balance memory
     * reservations on the machine and kill and frames that are out of control.
     *
     * @param host
     * @param report
     */
    private void handleMemoryReservations(final DispatchHost host, final HostReport report) {

        // TODO: GPU: Need to keep frames from growing into space reserved for GPU frames
        // However all this is done in the database without a chance to edit the values here

        /*
         * Check to see if we enable kill mode to free up memory.
         */
        boolean killMode = hostManager.isSwapping(host);

        for (final RunningFrameInfo f: report.frames) {

            VirtualProc p = hostManager.getVirtualProc(f.resourceId);

            // TODO: handle memory management for local dispatches
            // Skip local dispatches for now.
            if (p.isLocalDispatch) {
                continue;
            }

            try {
                if (f.rss > host.memory) {
                    try{
                        VirtualProc proc = hostManager.findVirtualProc(p);
                        logger.info("Killing frame " + f.jobName + "/" + f.frameName + ", "
                                + proc.getName() + " was OOM");
                        try {
                            killQueue.execute(new DispatchRqdKillFrame(proc, "The frame required " +
                                    CueUtil.KbToMb(f.rss) + "MB but the machine only has " +
                                    CueUtil.KbToMb(host.memory), rqdClient));
                        } catch (TaskRejectedException e) {
                            logger.warn("Unable to queue  RQD kill, task rejected, " + e);
                        }
                        DispatchSupport.killedOomProcs.incrementAndGet();
                    } catch (Exception e) {
                        logger.info("failed to kill frame on " + p.getName() +
                                "," + e);
                    }
                }

                if (dispatchSupport.increaseReservedMemory(p, f.rss)) {
                    p.memoryReserved = f.rss;
                    logger.info("frame " + f.frameName + " on job " + f.jobName
                            + " increased its reserved memory to " +
                            CueUtil.KbToMb((long)f.rss));
                }

            } catch (ResourceReservationFailureException e) {

                long memNeeded = f.rss - p.memoryReserved;

                logger.info("frame " + f.frameName + " on job " + f.jobName
                        + "was unable to reserve an additional " + CueUtil.KbToMb(memNeeded)
                        + "on proc " + p.getName() + ", " + e);

                try {
                    if (dispatchSupport.balanceReservedMemory(p, memNeeded)) {
                        p.memoryReserved = f.rss;
                        logger.info("was able to balance host: " + p.getName());
                    }
                    else {
                        logger.info("failed to balance host: " + p.getName());
                    }
                } catch (Exception ex) {
                    logger.warn("failed to balance host: " + p.getName() + ", " + e);
                }
            }
        }

        if (killMode) {
            VirtualProc proc;
            try {
                proc = hostManager.getWorstMemoryOffender(host);
            }
            catch (EmptyResultDataAccessException e) {
                logger.info(host.name + " is swapping and no proc is running on it.");
                return;
            }

            logger.info("Killing frame on " +
                    proc.getName() + ", host is distressed.");

            DispatchSupport.killedOffenderProcs.incrementAndGet();
            jobManagerSupport.kill(proc, new Source(
                    "The host was dangerously low on memory and swapping."));
        }
    }

    /**
     *  Update memory usage for the given list of frames.
     *
     * @param rFrames
     */
    private void updateMemoryUsage(List<RunningFrameInfo> rFrames) {

        for (RunningFrameInfo rf: rFrames) {

            Frame frame = jobManager.getFrame(rf.frameId);

            dispatchSupport.updateFrameMemoryUsage(frame,
                    rf.rss, rf.maxRss);

            dispatchSupport.updateProcMemoryUsage(frame,
                    rf.rss, rf.maxRss, rf.vsize, rf.maxVsize);
        }

        updateJobMemoryUsage(rFrames);
        updateLayerMemoryUsage(rFrames);
    }

    /**
     * Update job memory using for the given list of frames.
     *
     * @param frames
     */
    private void updateJobMemoryUsage(List<RunningFrameInfo> frames) {

        final Map<JobEntity, Long> jobs =
            new HashMap<JobEntity, Long>(frames.size());

        for (RunningFrameInfo frame: frames) {
            JobEntity job = new JobEntity(frame.jobId);
            if (jobs.containsKey(job)) {
                if (jobs.get(job) < frame.maxRss) {
                    jobs.put(job, frame.maxRss);
                }
            }
            else {
                jobs.put(job, frame.maxRss);
            }
        }

        for (Map.Entry<JobEntity,Long> set: jobs.entrySet()) {
            jobDao.updateMaxRSS(set.getKey(), set.getValue());
        }
    }

    /**
     * Update layer memory usage for the given list of frames.
     *
     * @param frames
     */
    private void updateLayerMemoryUsage(List<RunningFrameInfo> frames) {

        final Map<LayerEntity, Long> layers =
            new HashMap<LayerEntity, Long>(frames.size());

        for (RunningFrameInfo frame: frames) {
            LayerEntity layer = new LayerEntity(frame.layerId);
            if (layers.containsKey(layer)) {
                if (layers.get(layer) < frame.maxRss) {
                    layers.put(layer, frame.maxRss);
                }
            }
            else {
                layers.put(layer, frame.maxRss);
            }
        }

        /* Attempt to update the max RSS value for the job **/
        for (Map.Entry<LayerEntity,Long> set: layers.entrySet()) {
            layerDao.increaseLayerMinMemory(set.getKey(), set.getValue());
            layerDao.updateLayerMaxRSS(set.getKey(), set.getValue(), false);
        }
    }

    /**
     * Number of seconds before running frames have to exist before being
     * verified against the DB.
     */
    private static final long FRAME_VERIFICATION_GRACE_PERIOD_SECONDS = 120;

    /**
     * Verify all running frames in the given report against
     * the DB.  Frames that have not been running for at least
     * FRAME_VERIFICATION_GRACE_PERIOD_SECONDS are skipped.
     *
     * If a frame->proc mapping is not verified then the record
     * for the proc is pulled from the DB.  If the proc doesn't
     * exist at all, then the frame is killed with the message:
     * "but the DB did not reflect this"
     *
     * The main reason why a proc no longer exists is that the cue
     * though the host went down and cleared out all running frames.
     *
     * @param report
     */
    public void verifyRunningFrameInfo(HostReport report) {

        List<RunningFrameInfo> runningFrames = new
            ArrayList<RunningFrameInfo>(report.frames.size());

        for (RunningFrameInfo runningFrame: report.frames) {

            long runtimeSeconds = (System.currentTimeMillis() -
                runningFrame.startTime) / 1000l;

            // Don't test frames that haven't been running long enough.
            if (runtimeSeconds < FRAME_VERIFICATION_GRACE_PERIOD_SECONDS) {
                logger.info("verified " + runningFrame.jobName +
                        "/" + runningFrame.frameName + " on " +
                        report.host.name + " by grace period " +
                        runtimeSeconds + " seconds.");
                runningFrames.add(runningFrame);
                continue;
            }


            if (!hostManager.verifyRunningProc(runningFrame.resourceId,
                    runningFrame.frameId)) {

                /*
                 * The frame this proc is running is no longer
                 * assigned to this proc.   Don't ever touch
                 * the frame record.  If we make it here that means
                 * the proc has been running for over 2 min.
                 */

                String msg;
                VirtualProc proc = null;

                try {
                    proc = hostManager.getVirtualProc(runningFrame.resourceId);
                    msg = "Virutal proc " + proc.getProcId() +
                        "is assigned to " + proc.getFrameId() +
                        " not " + runningFrame.frameId;
                }
                catch (Exception e) {
                    /*
                     * This will happen if the host goes off line and then
                     * comes back.  In this case, we don't touch the frame
                     * since it might already be running somewhere else.  We
                     * do however kill the proc.
                     */
                    msg = "Virtual proc did not exist.";
                }

                logger.info("warning, the proc " +
                        runningFrame.resourceId + " on host " +
                        report.host.name + " was running for " +
                        (runtimeSeconds / 60.0f) + " minutes " +
                        runningFrame.jobName + "/" + runningFrame.frameName +
                        "but the DB did not " +
                        "reflect this " +
                        msg);

                DispatchSupport.accountingErrors.incrementAndGet();

                try {
                    /*
                     * If the proc did exist unbook it if we can't
                     * verify its running something.
                     */
                    boolean rqd_kill = false;
                    if (proc != null) {

                        /*
                         * Check to see if the proc is an orphan.
                         */
                        if (hostManager.isOprhan(proc)) {
                            dispatchSupport.clearVirtualProcAssignement(proc);
                            dispatchSupport.unbookProc(proc);
                            rqd_kill = true;
                        }
                    }
                    else {
                        /* Proc doesn't exist so a kill won't hurt */
                        rqd_kill = true;
                    }

                    if (rqd_kill) {
                        try {
                        killQueue.execute(new DispatchRqdKillFrame(report.host.name,
                                runningFrame.frameId,
                                "Cue3 could not verify this frame.",
                                rqdClient));
                        } catch (TaskRejectedException e) {
                            logger.warn("Unable to queue  RQD kill, task rejected, " + e);
                        }
                    }

                } catch (RqdClientException rqde) {
                    logger.warn("failed to kill " +
                            runningFrame.jobName + "/" +
                            runningFrame.frameName +
                            " when trying to clear a failed " +
                            " frame verification, " + rqde);

                } catch (Exception e) {
                    CueExceptionUtil.logStackTrace("failed", e);
                    logger.warn("failed to verify " +
                            runningFrame.jobName +"/" +
                            runningFrame.frameName +
                            " was running but the frame was " +
                            " unable to be killed, " + e);
                }
            }
            else {
                runningFrames.add(runningFrame);
            }
        }
        report.frames = runningFrames;
    }

    public HostManager getHostManager() {
        return hostManager;
    }

    public void setHostManager(HostManager hostManager) {
        this.hostManager = hostManager;
    }

    public BookingQueue getBookingQueue() {
        return bookingQueue;
    }

    public void setBookingQueue(BookingQueue bookingQueue) {
        this.bookingQueue = bookingQueue;
    }

    public ThreadPoolExecutor getReportQueue() {
        return reportQueue;
    }

    public void setReportQueue(ThreadPoolExecutor reportQueue) {
        this.reportQueue = reportQueue;
    }

    public DispatchSupport getDispatchSupport() {
        return dispatchSupport;
    }

    public void setDispatchSupport(DispatchSupport dispatchSupport) {
        this.dispatchSupport = dispatchSupport;
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public RqdClient getRqdClient() {
        return rqdClient;
    }

    public void setRqdClient(RqdClient rqdClient) {
        this.rqdClient = rqdClient;
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public void setJobManager(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    public JobManagerSupport getJobManagerSupport() {
        return jobManagerSupport;
    }

    public void setJobManagerSupport(JobManagerSupport jobManagerSupport) {
        this.jobManagerSupport = jobManagerSupport;
    }

    public JobDao getJobDao() {
        return jobDao;
    }

    public void setJobDao(JobDao jobDao) {
        this.jobDao = jobDao;
    }

    public LayerDao getLayerDao() {
        return layerDao;
    }

    public void setLayerDao(LayerDao layerDao) {
        this.layerDao = layerDao;
    }

    public BookingManager getBookingManager() {
        return bookingManager;
    }

    public void setBookingManager(BookingManager bookingManager) {
        this.bookingManager = bookingManager;
    }

    public Dispatcher getLocalDispatcher() {
        return localDispatcher;
    }

    public void setLocalDispatcher(Dispatcher localDispatcher) {
        this.localDispatcher = localDispatcher;
    }
    public ThreadPoolExecutor getKillQueue() {
        return killQueue;
    }

    public void setKillQueue(ThreadPoolExecutor killQueue) {
        this.killQueue = killQueue;
    }
}

