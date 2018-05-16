#  Copyright (c) 2018 Sony Pictures Imageworks Inc.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.



"""
Project: Cue3 Library

Module: exception.py - Provides Cue3 access to exceptions

Created: March 7, 2008

Contact: Middle-Tier Group (middle-tier@imageworks.com)

SVN: $Id$
"""
class CueException(Exception):
    """A Base class for all client side cue exceptions"""
    pass

class CuebotProxyCreationError(CueException):
    """Error creating Ice proxy"""
    pass


