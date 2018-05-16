
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

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.JdbcDaoSupport;

import com.imageworks.spcue.Facility;
import com.imageworks.spcue.FacilityEntity;
import com.imageworks.spcue.dao.FacilityDao;
import com.imageworks.spcue.util.SqlUtil;

public class FacilityDaoJdbc extends JdbcDaoSupport implements FacilityDao {

    public static final RowMapper<Facility> FACILITY_MAPPER = new RowMapper<Facility>() {
        public Facility mapRow(ResultSet rs, int rowNum) throws SQLException {
            FacilityEntity facility = new FacilityEntity();
            facility.id = rs.getString("pk_facility");
            facility.name = rs.getString("str_name");
            return facility;
        }
    };

    public Facility getDefaultFacility() {
        return getJdbcTemplate().queryForObject(
                "SELECT pk_facility,str_name FROM facility WHERE b_default=1 AND ROWNUM < 2",
                FACILITY_MAPPER);
    }

    public Facility getFacility(String id) {
        return getJdbcTemplate().queryForObject(
                "SELECT pk_facility, str_name FROM facility WHERE pk_facility=? " +
                "OR str_name=?", FACILITY_MAPPER, id, id);
    }

    public boolean facilityExists(String name) {
        return getJdbcTemplate().queryForObject(
                "SELECT COUNT(1) FROM facility WHERE str_name=?",
                Integer.class, name) > 0;

    }

    public Facility insertFacility(FacilityEntity facility) {
        facility.id = SqlUtil.genKeyRandom();

        getJdbcTemplate().update(
                "INSERT INTO facility (pk_facility, str_name) VALUES (?,?)",
                facility.getId(), facility.getName());

        return facility;
    }

    @Override
    public int deleteFacility(Facility facility) {
        return getJdbcTemplate().update(
                "DELETE FROM facility WHERE pk_facility = ?",
                facility.getFacilityId());
    }

    @Override
    public int updateFacilityName(Facility facility, String name) {
        return getJdbcTemplate().update(
                "UPDATE facility SET str_name=? WHERE pk_facility = ?",
                name, facility.getFacilityId());
    }

}

