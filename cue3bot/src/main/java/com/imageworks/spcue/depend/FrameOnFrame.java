
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



package com.imageworks.spcue.depend;

import com.imageworks.spcue.Frame;
import com.imageworks.spcue.CueIce.DependTarget;
import com.imageworks.spcue.CueIce.DependType;
import com.imageworks.spcue.util.SqlUtil;

public class FrameOnFrame extends AbstractDepend implements Depend {

    private final Frame dependErFrame;
    private final Frame dependOnFrame;
    private AbstractDepend parent = null;

    public FrameOnFrame(Frame dependErFrame,
            Frame dependOnFrame, AbstractDepend parent) {

        if (dependOnFrame.getFrameId().equals(dependErFrame.getFrameId())) {
            throw new DependException("The frame " + dependErFrame.getName() +
                    " cannot depend on itself.");
        }

        this.dependErFrame = dependErFrame;
        this.dependOnFrame = dependOnFrame;
        this.parent = parent;
    }
    public FrameOnFrame(Frame dependErFrame,
            Frame dependOnFrame) {
        this.dependErFrame = dependErFrame;
        this.dependOnFrame = dependOnFrame;
    }
    public Frame getDependErFrame() {
        return dependErFrame;
    }

    public Frame getDependOnFrame() {
        return dependOnFrame;
    }

    public AbstractDepend getParent() {
        return parent;
    }

    @Override
    public void accept(DependVisitor dependVisitor) {
        dependVisitor.accept(this);
    }

    @Override
    public String getSignature() {
        StringBuilder key = new StringBuilder(256);
        key.append(DependType.FrameOnFrame.toString());
        key.append(dependErFrame.getJobId());
        key.append(dependOnFrame.getJobId());
        key.append(dependErFrame.getFrameId());
        key.append(dependOnFrame.getFrameId());
        return SqlUtil.genKeyByName(key.toString());
    }

    @Override
    public DependTarget getTarget() {
        if (dependErFrame.getJobId().equals(dependOnFrame.getJobId())) {
            return DependTarget.Internal;
        }
        else {
            return DependTarget.External;
        }
    }
}

