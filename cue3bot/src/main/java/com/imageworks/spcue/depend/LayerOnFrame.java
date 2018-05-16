
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
import com.imageworks.spcue.Layer;
import com.imageworks.spcue.CueIce.DependTarget;
import com.imageworks.spcue.CueIce.DependType;
import com.imageworks.spcue.util.SqlUtil;

public class LayerOnFrame extends AbstractDepend implements Depend {

    private final Layer dependErLayer;
    private final Frame dependOnFrame;

    public LayerOnFrame(Layer dependErLayer, Frame dependOnFrame) {

        if (dependErLayer.getLayerId().equals(dependOnFrame.getLayerId())) {
            throw new DependException("A layer cannot depend on one of its own frames.");
        }

        this.dependErLayer = dependErLayer;
        this.dependOnFrame = dependOnFrame;
    }

    public Layer getDependErLayer() {
        return dependErLayer;
    }

    public Frame getDependOnFrame() {
        return dependOnFrame;
    }

    @Override
    public String getSignature() {
        StringBuilder key = new StringBuilder(256);
        key.append(DependType.JobOnJob.toString());
        key.append(dependErLayer.getLayerId());
        key.append(dependOnFrame.getFrameId());
        return SqlUtil.genKeyByName(key.toString());
    }

    @Override
    public void accept(DependVisitor dependVisitor) {
        dependVisitor.accept(this);
    }

    @Override
    public DependTarget getTarget() {
        if (dependErLayer.getJobId().equals(dependOnFrame.getJobId())) {
            return DependTarget.Internal;
        }
        else {
            return DependTarget.External;
        }
    }
}

