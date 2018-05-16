
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

import com.imageworks.spcue.SpcueRuntimeException;

/**
 * Thrown when a resource is created for an enity which already
 * has a resource assigned to it.
 */
@SuppressWarnings("serial")
public class ResourceDuplicationFailureException extends SpcueRuntimeException {

    public ResourceDuplicationFailureException() {
        // TODO Auto-generated constructor stub
    }

    public ResourceDuplicationFailureException(String message, Throwable cause) {
        super(message, cause);
        // TODO Auto-generated constructor stub
    }

    public ResourceDuplicationFailureException(String message) {
        super(message);
        // TODO Auto-generated constructor stub
    }

    public ResourceDuplicationFailureException(Throwable cause) {
        super(cause);
        // TODO Auto-generated constructor stub
    }

}

