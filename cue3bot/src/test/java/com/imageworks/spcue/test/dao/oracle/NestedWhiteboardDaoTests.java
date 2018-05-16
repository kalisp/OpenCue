
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



package com.imageworks.spcue.test.dao.oracle;

import static org.junit.Assert.*;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Test;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;

import com.imageworks.spcue.config.TestAppConfig;
import com.imageworks.spcue.ShowDetail;
import com.imageworks.spcue.CueClientIce.NestedHost;
import com.imageworks.spcue.dao.NestedWhiteboardDao;
import com.imageworks.spcue.dao.ShowDao;

@Transactional
@ContextConfiguration(classes=TestAppConfig.class, loader=AnnotationConfigContextLoader.class)
@TransactionConfiguration(transactionManager="transactionManager")
public class NestedWhiteboardDaoTests extends AbstractTransactionalJUnit4SpringContextTests {

    @Resource
    NestedWhiteboardDao nestedWhiteboardDao;

    @Resource
    ShowDao showDao;

    public ShowDetail getShow() {
        return showDao.findShowDetail("pipe");
    }

    @Test
    @Transactional
    @Rollback(true)
    public void testGetNestedJobWhiteboard() {
        nestedWhiteboardDao.getJobWhiteboard(getShow());
        nestedWhiteboardDao.getJobWhiteboard(getShow());
        nestedWhiteboardDao.getJobWhiteboard(getShow());
        nestedWhiteboardDao.getJobWhiteboard(getShow());
        nestedWhiteboardDao.getJobWhiteboard(getShow());
    }
}

