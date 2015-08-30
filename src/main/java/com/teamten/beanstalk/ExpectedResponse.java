package com.teamten.beanstalk;

/*
 *
 * Copyright 2009-2010 Robert Tykulsker *
 * This file is part of JavaBeanstalkCLient.
 *
 * JavaBeanstalkCLient is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version, or alternatively, the BSD license
 * supplied
 * with this project in the file "BSD-LICENSE".
 *
 * JavaBeanstalkCLient is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JavaBeanstalkCLient. If not, see <http://www.gnu.org/licenses/>.
 *
 */

/**
 * Enumerates the types of responses that we expect from the server.
 */
public enum ExpectedResponse {
  /**
   * The command does not contain data.
   */
  NONE(0),

  /**
   * The command contains raw data, such as a job.
   */
  BYTE_ARRAY(1),

  /**
   * The command contains a YAML document with a list of strings.
   */
  LIST(2),

  /**
   * The command contains a YAML document with a map from string to string.
   */
  MAP(3);

  private final int id;

  ExpectedResponse(final int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }

  public static ExpectedResponse getById(final int id) {
    for (final ExpectedResponse t : values()) {
      if (t.id == id) {
        return t;
      }
    }
    return null;
  }
}
