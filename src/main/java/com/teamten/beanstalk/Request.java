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

import lombok.Getter;

/**
 * Encapsulates a request to the server.
 */
class Request {

  @Getter
  private final String command;

  @Getter
  private final String[] validStates;

  @Getter
  private final String[] errorStates;

  @Getter
  private final byte[] data;

  @Getter
  private final ExpectedResponse expectedResponse;

  @Getter
  private final int expectedDataLengthIndex;

  /**
   * @param command                 beanstalkd command
   * @param validStates             Array of valid states
   * @param errorStates             Array of error states
   * @param data                    Byte array of data
   * @param expectedResponse        Expected response type
   * @param expectedDataLengthIndex Expected length of data
   */
  public Request(
      final String command,
      final String[] validStates,
      final String[] errorStates,
      final byte[] data,
      final ExpectedResponse expectedResponse,
      final int expectedDataLengthIndex
  ) {
    this.command = command;
    this.validStates = validStates;
    this.errorStates = errorStates;
    this.data = data;
    this.expectedResponse = expectedResponse;
    this.expectedDataLengthIndex = expectedDataLengthIndex;
  }

  /**
   * @param command          beanstalkd command
   * @param validStates      Array of valid states
   * @param errorStates      Array of error states
   * @param data             Byte array of data
   * @param expectedResponse Expected response type
   */
  public Request(
      final String command,
      final String[] validStates,
      final String[] errorStates,
      final byte[] data,
      final ExpectedResponse expectedResponse
  ) {
    this(command, validStates, errorStates, data, expectedResponse, -1);
  }
}
