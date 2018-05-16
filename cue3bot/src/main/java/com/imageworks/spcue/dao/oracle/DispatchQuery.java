
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



package com.imageworks.spcue.dao.oracle;

public class DispatchQuery {

    public static final String FIND_JOBS_BY_SHOW =
        "/* FIND_JOBS_BY_SHOW */ SELECT pk_job, float_tier, rank FROM ( " +
            "SELECT " +
                "ROW_NUMBER() OVER (ORDER BY " +
                    "point.float_tier ASC, " +
                    "folder_resource.float_tier ASC, " +
                    "job_resource.float_tier ASC " +
                    ") AS rank," +
                "job.pk_job, " +
                "job_resource.float_tier " +
            "FROM " +
                "job            , " +
                "job_resource   , " +
                "folder         , " +
                "folder_resource, " +
                "point          , " +
                "layer          , " +
                "layer_stat     , " +
                "host             " +
            "WHERE " +
                "job.pk_job                 = job_resource.pk_job " +
                "AND job.pk_folder          = folder.pk_folder " +
                "AND folder.pk_folder       = folder_resource.pk_folder " +
                "AND folder.pk_dept         = point.pk_dept " +
                "AND folder.pk_show         = point.pk_show " +
                "AND job.pk_job             = layer.pk_job " +
                "AND job_resource.pk_job    = job.pk_job " +
                "AND (CASE WHEN layer_stat.int_waiting_count > 0 THEN layer_stat.pk_layer ELSE NULL END) = layer.pk_layer " +
                "AND " +
                    "(" +
                        "folder_resource.int_max_cores = -1 " +
                    "OR " +
                        "folder_resource.int_cores < folder_resource.int_max_cores " +
                    ") " +
                "AND job.str_state                  = 'Pending' " +
                "AND job.b_paused                   = 0 " +
                "AND job.pk_show                    = ? " +
                "AND job.pk_facility                = ? " +
                "AND job.str_os                     = ? " +
                "AND (CASE WHEN layer_stat.int_waiting_count > 0 THEN 1 ELSE NULL END) = 1 " +
                "AND layer.int_cores_min            <= ? " +
                "AND layer.int_mem_min              <= ? " +
                "AND layer.b_threadable             >= ? " +
                "AND layer.int_gpu_min              BETWEEN ? AND ? " +
                "AND job_resource.int_cores + layer.int_cores_min < job_resource.int_max_cores " +
                "AND CATSEARCH(host.str_tags, layer.str_tags, ?) > 0 " +
        ") WHERE rank < ?";


    public static final String FIND_JOBS_BY_GROUP =
        FIND_JOBS_BY_SHOW
            .replace(
                "FIND_JOBS_BY_SHOW",
                "FIND_JOBS_BY_GROUP")
            .replace(
                "AND job.pk_show                    = ? ",
                "AND job.pk_folder                  = ? ");


    /**
     * Dispatch a host in local booking mode.
     */
    public static final String FIND_JOBS_BY_LOCAL =
        "/* FIND_JOBS_BY_LOCAL */ SELECT pk_job,float_tier, rank FROM ( " +
        "SELECT " +
            "ROW_NUMBER() OVER (ORDER BY " +
                "host_local.float_tier ASC " +
            ") AS rank, " +
            "job.pk_job, " +
            "host_local.float_tier " +
        "FROM " +
            "job, " +
            "host_local " +
        "WHERE " +
            "job.pk_job = host_local.pk_job " +
        "AND " +
            "host_local.pk_host = ? " +
        "AND " +
            "job.str_state = 'Pending' " +
        "AND " +
            "job.b_paused = 0 " +
        "AND " +
            "job.pk_facility =  ? " +
        "AND " +
            "job.str_os = ? " +
        "AND " +
            "job.pk_job IN ( " +
                "SELECT " +
                    "l.pk_job " +
                "FROM " +
                    "job j, " +
                    "layer l, " +
                    "layer_stat lst, " +
                    "host h, " +
                    "host_local " +
                "WHERE " +
                    "j.pk_job = l.pk_job " +
                "AND " +
                    "j.pk_job = host_local.pk_job " +
                "AND " +
                    "h.pk_host = host_local.pk_host " +
                "AND " +
                    "h.pk_host = ? " +
                "AND " +
                    "j.str_state = 'Pending' " +
                "AND " +
                    "j.b_paused = 0 " +
                "AND " +
                    "j.pk_facility = ? " +
                "AND " +
                    "j.str_os = ? " +
                "AND " +
                    "(CASE WHEN lst.int_waiting_count > 0 THEN lst.pk_layer ELSE NULL END) = l.pk_layer " +
                "AND " +
                    "(CASE WHEN lst.int_waiting_count > 0 THEN 1 ELSE NULL END) = 1 " +
                "AND " +
                    "l.int_mem_min <= host_local.int_mem_idle " +
                "AND " +
                    "l.int_gpu_min <= host_local.int_gpu_idle " +
        ")) WHERE rank < 5";

    /**
     * This query is run before a proc is dispatched to the next frame.
     * It checks to see if there is another job someplace that is
     * under its minimum and can take the proc.
     *
     * The current job the proc is on is excluded.  This should only brun
     * if the exluded job is actually over its min proc.
     *
     * Does not unbook for Utility frames
     *
     */
    public static final String FIND_UNDER_PROCED_JOB_BY_FACILITY =
        "SELECT " +
            "1 " +
        "FROM " +
            "job, " +
            "job_resource, " +
            "folder, " +
            "folder_resource " +
        "WHERE " +
            "job.pk_job = job_resource.pk_job " +
        "AND " +
            "job.pk_folder = folder.pk_folder " +
        "AND " +
            "folder.pk_folder = folder_resource.pk_folder " +
        "AND " +
            "(folder_resource.int_max_cores = -1 OR folder_resource.int_cores < folder_resource.int_max_cores) " +
        "AND " +
            "job_resource.float_tier < 1.00 " +
        "AND " +
            "job_resource.int_cores < job_resource.int_max_cores " +
        "AND " +
            "job.str_state = 'Pending' " +
        "AND " +
            "job.b_paused = 0 " +
        "AND " +
            "job.pk_show = ? " +
        "AND " +
            "job.pk_facility = ? " +
        "AND " +
            "job.str_os = ? " +
        "AND " +
            "job.pk_job IN ( " +
                "SELECT /* index (h i_str_host_tag) */ " +
                    "l.pk_job " +
                "FROM " +
                    "job j, " +
                    "layer l, " +
                    "layer_stat lst, " +
                    "host h " +
                "WHERE " +
                    "j.pk_job = l.pk_job " +
                "AND " +
                    "j.str_state = 'Pending' " +
                "AND " +
                    "j.b_paused = 0 " +
                "AND " +
                    "j.pk_show = ? " +
                "AND " +
                    "j.pk_facility = ? " +
                "AND " +
                    "j.str_os = ? " +
                "AND " +
                    "(CASE WHEN lst.int_waiting_count > 0 THEN lst.pk_layer ELSE NULL END) = l.pk_layer " +
                "AND " +
                    "(CASE WHEN lst.int_waiting_count > 0 THEN 1 ELSE NULL END) = 1 " +
                "AND " +
                    "l.int_cores_min <= ? " +
                "AND " +
                    "l.int_mem_min <= ? " +
                "AND " +
                    "l.int_gpu_min = ? " +
                "AND " +
                    "CATSEARCH(h.str_tags, l.str_tags, ?) > 0) " +
    "AND ROWNUM < 2 ";

    /**
     * Finds the next frame in a job for a proc.
     */
    public static final String FIND_DISPATCH_FRAME_BY_JOB_AND_PROC =
        "SELECT "+
            "show_name, "+
            "job_name, " +
            "pk_job,"+
            "pk_show,"+
            "pk_facility,"+
            "str_name,"+
            "str_shot,"+
            "str_user,"+
            "int_uid,"+
            "str_log_dir,"+
            "frame_name, "+
            "frame_state, "+
            "pk_frame, "+
            "pk_layer, "+
            "int_retries, "+
            "int_version, " +
            "layer_name, " +
            "layer_type, "+
            "b_threadable,"+
            "int_cores_min,"+
            "int_cores_max,"+
            "int_mem_min,"+
            "int_gpu_min,"+
            "str_cmd, "+
            "str_range,"+
            "int_chunk_size, "+
            "str_services " +
        "FROM (SELECT " +
            "ROW_NUMBER() OVER ( ORDER BY " +
                "frame.int_dispatch_order ASC, " +
                "frame.int_layer_order ASC " +
            ") LINENUM, " +
            "job.str_show AS show_name, "+
            "job.str_name AS job_name, " +
            "job.pk_job,"+
            "job.pk_show,"+
            "job.pk_facility,"+
            "job.str_name,"+
            "job.str_shot,"+
            "job.str_user,"+
            "job.int_uid,"+
            "job.str_log_dir,"+
            "frame.str_name AS frame_name, "+
            "frame.str_state AS frame_state, "+
            "frame.pk_frame, "+
            "frame.pk_layer, "+
            "frame.int_retries, "+
            "frame.int_version, " +
            "layer.str_name AS layer_name, " +
            "layer.str_type AS layer_type, "+
            "layer.b_threadable,"+
            "layer.int_cores_min,"+
            "layer.int_cores_max,"+
            "layer.int_mem_min,"+
            "layer.int_gpu_min,"+
            "layer.str_cmd, "+
            "layer.str_range, "+
            "layer.int_chunk_size, "+
            "layer.str_services " +
        "FROM " +
            "job,"+
            "frame," +
            "layer " +
        "WHERE " +
            "frame.pk_layer = layer.pk_layer " +
        "AND " +
            "layer.pk_job = job.pk_job " +
        "AND " +
            "layer.int_cores_min <= ? " +
        "AND " +
            "layer.int_mem_min <= ? " +
        "AND " +
            "layer.int_gpu_min BETWEEN ? AND ? " +
        "AND " +
            "frame.str_state='Waiting' " +
        "AND " +
            "job.pk_job=? "+
        "AND layer.pk_layer IN ( " +
            "SELECT /*+ index (h i_str_host_tag) */ " +
                "pk_layer " +
            "FROM " +
                "layer l,"+
                "host h " +
            "WHERE " +
                "l.pk_job= ? " +
            "AND " +
                "CATSEARCH(h.str_tags, l.str_tags, ?) > 0 "+
        ")) WHERE LINENUM <= ?";

    /**
     * Find the next frame in a job for a host.
     */
    public static final String FIND_DISPATCH_FRAME_BY_JOB_AND_HOST =
        "SELECT " +
            "show_name, "+
            "job_name, " +
            "pk_job,"+
            "pk_show,"+
            "pk_facility,"+
            "str_name,"+
            "str_shot,"+
            "str_user,"+
            "int_uid,"+
            "str_log_dir,"+
            "frame_name, "+
            "frame_state, "+
            "pk_frame, "+
            "pk_layer, "+
            "int_retries, "+
            "int_version, " +
            "layer_name, " +
            "layer_type, "+
            "int_cores_min,"+
            "int_cores_max,"+
            "b_threadable,"+
            "int_mem_min,"+
            "int_gpu_min,"+
            "str_cmd, "+
            "str_range,"+
            "int_chunk_size, "+
            "str_services " +
        "FROM (SELECT " +
            "ROW_NUMBER() OVER ( ORDER BY " +
                "frame.int_dispatch_order ASC, " +
                "frame.int_layer_order ASC " +
            ") LINENUM, " +
            "job.str_show AS show_name, "+
            "job.str_name AS job_name, " +
            "job.pk_job,"+
            "job.pk_show,"+
            "job.pk_facility,"+
            "job.str_name,"+
            "job.str_shot,"+
            "job.str_user,"+
            "job.int_uid,"+
            "job.str_log_dir,"+
            "frame.str_name AS frame_name, "+
            "frame.str_state AS frame_state, "+
            "frame.pk_frame, "+
            "frame.pk_layer, "+
            "frame.int_retries, "+
            "frame.int_version, "+
            "layer.str_name AS layer_name, " +
            "layer.str_type AS layer_type, "+
            "layer.int_cores_min,"+
            "layer.int_cores_max,"+
            "layer.b_threadable,"+
            "layer.int_mem_min,"+
            "layer.int_gpu_min,"+
            "layer.str_cmd, "+
            "layer.str_range, "+
            "layer.int_chunk_size, "+
            "layer.str_services " +
        "FROM " +
            "job,"+
            "frame," +
            "layer " +
        "WHERE " +
            "frame.pk_layer = layer.pk_layer " +
        "AND " +
            "layer.pk_job = job.pk_job " +
        "AND " +
            "layer.int_cores_min <= ? " +
        "AND " +
            "layer.int_mem_min <= ? " +
        "AND " +
            "layer.b_threadable >= ? " +
        "AND " +
            "layer.int_gpu_min BETWEEN ? AND ? " +
        "AND " +
            "frame.str_state='Waiting' " +
        "AND " +
            "job.pk_job=? "+
        "AND " +
            "layer.pk_layer IN ( " +
            "SELECT /*+ index (h i_str_host_tag) */ " +
                "pk_layer " +
            "FROM " +
                "layer l,"+
                "host h " +
            "WHERE " +
                "l.pk_job=? " +
            "AND " +
                "CATSEARCH(h.str_tags, l.str_tags,?) > 0 "+
        ") " +
        ") WHERE LINENUM <= ?";


    public static final String FIND_LOCAL_DISPATCH_FRAME_BY_JOB_AND_PROC =
        "SELECT "+
            "show_name, "+
            "job_name, " +
            "pk_job,"+
            "pk_show,"+
            "pk_facility,"+
            "str_name,"+
            "str_shot,"+
            "str_user,"+
            "int_uid,"+
            "str_log_dir,"+
            "frame_name, "+
            "frame_state, "+
            "pk_frame, "+
            "pk_layer, "+
            "int_retries, "+
            "int_version, " +
            "layer_name, " +
            "layer_type, "+
            "b_threadable,"+
            "int_cores_min,"+
            "int_cores_max,"+
            "int_mem_min,"+
            "int_gpu_min,"+
            "str_cmd, "+
            "str_range,"+
            "int_chunk_size, "+
            "str_services " +
        "FROM (SELECT " +
            "ROW_NUMBER() OVER ( ORDER BY " +
                "frame.int_dispatch_order ASC, " +
                "frame.int_layer_order ASC " +
            ") LINENUM, " +
            "job.str_show AS show_name, "+
            "job.str_name AS job_name, " +
            "job.pk_job,"+
            "job.pk_show,"+
            "job.pk_facility,"+
            "job.str_name,"+
            "job.str_shot,"+
            "job.str_user,"+
            "job.int_uid,"+
            "job.str_log_dir,"+
            "frame.str_name AS frame_name, "+
            "frame.str_state AS frame_state, "+
            "frame.pk_frame, "+
            "frame.pk_layer, "+
            "frame.int_retries, "+
            "frame.int_version, " +
            "layer.str_name AS layer_name, " +
            "layer.str_type AS layer_type, "+
            "layer.b_threadable,"+
            "layer.int_cores_min,"+
            "layer.int_cores_max,"+
            "layer.int_mem_min,"+
            "layer.int_gpu_min,"+
            "layer.str_cmd, "+
            "layer.str_range, "+
            "layer.int_chunk_size, "+
            "layer.str_services " +
        "FROM " +
            "job,"+
            "frame," +
            "layer " +
        "WHERE " +
            "frame.pk_layer = layer.pk_layer " +
        "AND " +
            "layer.pk_job = job.pk_job " +
        "AND " +
            "layer.int_mem_min <= ? " +
        "AND " +
            "layer.int_gpu_min <= ? " +
        "AND " +
            "frame.str_state='Waiting' " +
        "AND " +
            "job.pk_job=? "+
        ") WHERE LINENUM <= ?";

    /**
     * Find the next frame in a job for a host.
     */
    public static final String FIND_LOCAL_DISPATCH_FRAME_BY_JOB_AND_HOST =
        "SELECT " +
            "show_name, "+
            "job_name, " +
            "pk_job,"+
            "pk_show,"+
            "pk_facility,"+
            "str_name,"+
            "str_shot,"+
            "str_user,"+
            "int_uid,"+
            "str_log_dir,"+
            "frame_name, "+
            "frame_state, "+
            "pk_frame, "+
            "pk_layer, "+
            "int_retries, "+
            "int_version, " +
            "layer_name, " +
            "layer_type, "+
            "int_cores_min,"+
            "int_cores_max,"+
            "b_threadable,"+
            "int_mem_min,"+
            "int_gpu_min,"+
            "str_cmd, "+
            "str_range,"+
            "int_chunk_size, "+
            "str_services " +
        "FROM (SELECT " +
            "ROW_NUMBER() OVER ( ORDER BY " +
                "frame.int_dispatch_order ASC, " +
                "frame.int_layer_order ASC " +
            ") LINENUM, " +
            "job.str_show AS show_name, "+
            "job.str_name AS job_name, " +
            "job.pk_job,"+
            "job.pk_show,"+
            "job.pk_facility,"+
            "job.str_name,"+
            "job.str_shot,"+
            "job.str_user,"+
            "job.int_uid,"+
            "job.str_log_dir,"+
            "frame.str_name AS frame_name, "+
            "frame.str_state AS frame_state, "+
            "frame.pk_frame, "+
            "frame.pk_layer, "+
            "frame.int_retries, "+
            "frame.int_version, "+
            "layer.str_name AS layer_name, " +
            "layer.str_type AS layer_type, "+
            "layer.int_cores_min,"+
            "layer.int_cores_max,"+
            "layer.b_threadable,"+
            "layer.int_mem_min,"+
            "layer.int_gpu_min,"+
            "layer.str_cmd, "+
            "layer.str_range, "+
            "layer.int_chunk_size, "+
            "layer.str_services " +
        "FROM " +
            "job,"+
            "frame," +
            "layer " +
        "WHERE " +
            "frame.pk_layer = layer.pk_layer " +
        "AND " +
            "layer.pk_job = job.pk_job " +
        "AND " +
            "layer.int_mem_min <= ? " +
        "AND " +
            "layer.int_gpu_min <= ? " +
        "AND " +
            "frame.str_state='Waiting' " +
        "AND " +
            "job.pk_job=? "+
        ") WHERE LINENUM <= ?";


    /**** LAYER DISPATCHING **/

    /**
     * Finds the next frame in a job for a proc.
     */
    public static final String FIND_DISPATCH_FRAME_BY_LAYER_AND_PROC =

        "SELECT "+
            "show_name, "+
            "job_name, " +
            "pk_job,"+
            "pk_show,"+
            "pk_facility,"+
            "str_name,"+
            "str_shot,"+
            "str_user,"+
            "int_uid,"+
            "str_log_dir,"+
            "frame_name, "+
            "frame_state, "+
            "pk_frame, "+
            "pk_layer, "+
            "int_retries, "+
            "int_version, " +
            "layer_name, " +
            "layer_type, "+
            "b_threadable,"+
            "int_cores_min,"+
            "int_cores_max,"+
            "int_mem_min,"+
            "int_gpu_min,"+
            "str_cmd, "+
            "str_range,"+
            "int_chunk_size, "+
            "str_services " +
        "FROM (SELECT " +
            "ROW_NUMBER() OVER ( ORDER BY " +
                "frame.int_dispatch_order ASC, " +
                "frame.int_layer_order ASC " +
            ") LINENUM, " +
            "job.str_show AS show_name, "+
            "job.str_name AS job_name, " +
            "job.pk_job,"+
            "job.pk_show,"+
            "job.pk_facility,"+
            "job.str_name,"+
            "job.str_shot,"+
            "job.str_user,"+
            "job.int_uid,"+
            "job.str_log_dir,"+
            "frame.str_name AS frame_name, "+
            "frame.str_state AS frame_state, "+
            "frame.pk_frame, "+
            "frame.pk_layer, "+
            "frame.int_retries, "+
            "frame.int_version, " +
            "layer.str_name AS layer_name, " +
            "layer.str_type AS layer_type, "+
            "layer.b_threadable,"+
            "layer.int_cores_min,"+
            "layer.int_cores_max,"+
            "layer.int_mem_min,"+
            "layer.int_gpu_min,"+
            "layer.str_cmd, "+
            "layer.str_range, "+
            "layer.int_chunk_size, "+
            "layer.str_services " +
        "FROM " +
            "job,"+
            "frame," +
            "layer " +
        "WHERE " +
            "frame.pk_layer = layer.pk_layer " +
        "AND " +
            "layer.pk_job = job.pk_job " +
        "AND " +
            "layer.int_cores_min <= ? " +
        "AND " +
            "layer.int_mem_min <= ? " +
        "AND " +
            "layer.int_gpu_min = ? " +
        "AND " +
            "frame.str_state='Waiting' " +
        "AND " +
            "job.pk_layer=? "+
        "AND layer.pk_layer IN ( " +
            "SELECT /*+ index (h i_str_host_tag) */ " +
                "pk_layer " +
            "FROM " +
                "layer l,"+
                "host h " +
            "WHERE " +
                "l.pk_layer= ? " +
            "AND " +
                "CATSEARCH(h.str_tags, l.str_tags, ?) > 0 "+
        ")) WHERE LINENUM <= ?";

    /**
     * Find the next frame in a job for a host.
     */
    public static final String FIND_DISPATCH_FRAME_BY_LAYER_AND_HOST =
        "SELECT " +
            "show_name, "+
            "job_name, " +
            "pk_job,"+
            "pk_show,"+
            "pk_facility,"+
            "str_name,"+
            "str_shot,"+
            "str_user,"+
            "int_uid,"+
            "str_log_dir,"+
            "frame_name, "+
            "frame_state, "+
            "pk_frame, "+
            "pk_layer, "+
            "int_retries, "+
            "int_version, " +
            "layer_name, " +
            "layer_type, "+
            "int_cores_min,"+
            "int_cores_max,"+
            "b_threadable,"+
            "int_mem_min,"+
            "int_gpu_min,"+
            "str_cmd, "+
            "str_range,"+
            "int_chunk_size, "+
            "str_services " +
        "FROM (SELECT " +
            "ROW_NUMBER() OVER ( ORDER BY " +
                "frame.int_dispatch_order ASC, " +
                "frame.int_layer_order ASC " +
            ") LINENUM, " +
            "job.str_show AS show_name, "+
            "job.str_name AS job_name, " +
            "job.pk_job,"+
            "job.pk_show,"+
            "job.pk_facility,"+
            "job.str_name,"+
            "job.str_shot,"+
            "job.str_user,"+
            "job.int_uid,"+
            "job.str_log_dir,"+
            "frame.str_name AS frame_name, "+
            "frame.str_state AS frame_state, "+
            "frame.pk_frame, "+
            "frame.pk_layer, "+
            "frame.int_retries, "+
            "frame.int_version, "+
            "layer.str_name AS layer_name, " +
            "layer.str_type AS layer_type, "+
            "layer.int_cores_min,"+
            "layer.int_cores_max,"+
            "layer.b_threadable,"+
            "layer.int_mem_min,"+
            "layer.int_gpu_min,"+
            "layer.str_cmd, "+
            "layer.str_range, "+
            "layer.int_chunk_size, "+
            "layer.str_services " +
        "FROM " +
            "job,"+
            "frame," +
            "layer " +
        "WHERE " +
            "frame.pk_layer = layer.pk_layer " +
        "AND " +
            "layer.pk_job = job.pk_job " +
        "AND " +
            "layer.int_cores_min <= ? " +
        "AND " +
            "layer.int_mem_min <= ? " +
        "AND " +
            "layer.b_threadable >= ? " +
        "AND " +
            "layer.int_gpu_min <= ? " +
        "AND " +
            "frame.str_state='Waiting' " +
        "AND " +
            "layer.pk_layer=? "+
        "AND " +
            "layer.pk_layer IN ( " +
            "SELECT /*+ index (h i_str_host_tag) */ " +
                "pk_layer " +
            "FROM " +
                "layer l,"+
                "host h " +
            "WHERE " +
                "l.pk_layer=? " +
            "AND " +
                "CATSEARCH(h.str_tags, l.str_tags,?) > 0 "+
        ") " +
        ") WHERE LINENUM <= ?";


    public static final String FIND_LOCAL_DISPATCH_FRAME_BY_LAYER_AND_PROC =
        "SELECT "+
            "show_name, "+
            "job_name, " +
            "pk_job,"+
            "pk_show,"+
            "pk_facility,"+
            "str_name,"+
            "str_shot,"+
            "str_user,"+
            "int_uid,"+
            "str_log_dir,"+
            "frame_name, "+
            "frame_state, "+
            "pk_frame, "+
            "pk_layer, "+
            "int_retries, "+
            "int_version, " +
            "layer_name, " +
            "layer_type, "+
            "b_threadable,"+
            "int_cores_min,"+
            "int_cores_max,"+
            "int_mem_min,"+
            "int_gpu_min,"+
            "str_cmd, "+
            "str_range,"+
            "int_chunk_size, "+
            "str_services " +
        "FROM (SELECT " +
            "ROW_NUMBER() OVER ( ORDER BY " +
                "frame.int_dispatch_order ASC, " +
                "frame.int_layer_order ASC " +
            ") LINENUM, " +
            "job.str_show AS show_name, "+
            "job.str_name AS job_name, " +
            "job.pk_job,"+
            "job.pk_show,"+
            "job.pk_facility,"+
            "job.str_name,"+
            "job.str_shot,"+
            "job.str_user,"+
            "job.int_uid,"+
            "job.str_log_dir,"+
            "frame.str_name AS frame_name, "+
            "frame.str_state AS frame_state, "+
            "frame.pk_frame, "+
            "frame.pk_layer, "+
            "frame.int_retries, "+
            "frame.int_version, " +
            "layer.str_name AS layer_name, " +
            "layer.str_type AS layer_type, "+
            "layer.b_threadable,"+
            "layer.int_cores_min,"+
            "layer.int_mem_min,"+
            "layer.int_gpu_min,"+
            "layer.int_cores_max,"+
            "layer.str_cmd, "+
            "layer.str_range, "+
            "layer.int_chunk_size, "+
            "layer.str_services " +
        "FROM " +
            "job,"+
            "frame," +
            "layer " +
        "WHERE " +
            "frame.pk_layer = layer.pk_layer " +
        "AND " +
            "layer.pk_job = job.pk_job " +
        "AND " +
            "layer.int_mem_min <= ? " +
        "AND " +
            "layer.int_gpu_min <= ? " +
        "AND " +
            "frame.str_state='Waiting' " +
        "AND " +
            "layer.pk_layer =? "+
        ") WHERE LINENUM <= ?";

    /**
     * Find the next frame in a job for a host.
     */
    public static final String FIND_LOCAL_DISPATCH_FRAME_BY_LAYER_AND_HOST =
        "SELECT " +
            "show_name, "+
            "job_name, " +
            "pk_job,"+
            "pk_show,"+
            "pk_facility,"+
            "str_name,"+
            "str_shot,"+
            "str_user,"+
            "int_uid,"+
            "str_log_dir,"+
            "frame_name, "+
            "frame_state, "+
            "pk_frame, "+
            "pk_layer, "+
            "int_retries, "+
            "int_version, " +
            "layer_name, " +
            "layer_type, "+
            "int_cores_min,"+
            "int_cores_max,"+
            "b_threadable,"+
            "int_mem_min,"+
            "int_gpu_min,"+
            "str_cmd, "+
            "str_range,"+
            "int_chunk_size, "+
            "str_services " +
        "FROM (SELECT " +
            "ROW_NUMBER() OVER ( ORDER BY " +
                "frame.int_dispatch_order ASC, " +
                "frame.int_layer_order ASC " +
            ") LINENUM, " +
            "job.str_show AS show_name, "+
            "job.str_name AS job_name, " +
            "job.pk_job,"+
            "job.pk_show,"+
            "job.pk_facility,"+
            "job.str_name,"+
            "job.str_shot,"+
            "job.str_user,"+
            "job.int_uid,"+
            "job.str_log_dir,"+
            "frame.str_name AS frame_name, "+
            "frame.str_state AS frame_state, "+
            "frame.pk_frame, "+
            "frame.pk_layer, "+
            "frame.int_retries, "+
            "frame.int_version, "+
            "layer.str_name AS layer_name, " +
            "layer.str_type AS layer_type, "+
            "layer.int_cores_min,"+
            "layer.int_cores_max,"+
            "layer.b_threadable,"+
            "layer.int_mem_min,"+
            "layer.int_gpu_min,"+
            "layer.str_cmd, "+
            "layer.str_range, "+
            "layer.int_chunk_size, "+
            "layer.str_services "+
        "FROM " +
            "job,"+
            "frame," +
            "layer " +
        "WHERE " +
            "frame.pk_layer = layer.pk_layer " +
        "AND " +
            "layer.pk_job = job.pk_job " +
        "AND " +
            "layer.int_mem_min <= ? " +
        "AND " +
            "layer.int_gpu_min <= ? " +
        "AND " +
            "frame.str_state='Waiting' " +
        "AND " +
            "layer.pk_layer=? "+
        ") WHERE LINENUM <= ?";

    /**
     * Looks for shows that are under their burst for a particular
     * type of proc.  The show has to be at least one whole proc
     * under their burst to be considered for booking.
     */
    public static final String FIND_SHOWS =
        "SELECT " +
            "vs_waiting.pk_show,"+
            "s.float_tier, " +
            "s.int_burst " +
        "FROM " +
            "subscription s,"+
            "vs_waiting " +
        "WHERE "+
            "vs_waiting.pk_show = s.pk_show " +
        "AND " +
            "s.pk_alloc = ? " +
        "AND " +
            "s.int_burst > 0 " +
        "AND " +
             "s.int_burst - s.int_cores >= 100 " +
        "AND " +
            "s.int_cores < s.int_burst ";

}

