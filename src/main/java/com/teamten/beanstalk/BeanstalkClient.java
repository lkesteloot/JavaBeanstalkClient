package com.teamten.beanstalk;

/*
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Interface for the Beanstalk client.
 */
public interface BeanstalkClient {

  /**
   * By default we connect to the localhost.
   */
  String DEFAULT_HOST = "localhost";

  /**
   * The server uses this port by default.
   */
  int DEFAULT_PORT = 11300;

  /**
   * Put a message into a tube.
   *
   * @param priority     A number between 0 (most urgent) inclusive and 2**32 (least urgent)
   *                     exclusive.
   * @param delaySeconds The number of seconds to wait before delivering the job.
   * @param timeToRun    The number of seconds that the worker has to perform the job.
   * @param data         The raw data for the message.
   * @return the job ID.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  long put(long priority, int delaySeconds, int timeToRun, byte[] data) throws IOException;

  /**
   * Specify which tube to put future jobs into.
   *
   * @param tubeName Which tube to put jobs into. If not specified, defaults to "default".
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  void useTube(String tubeName) throws IOException;

  /**
   * Pull a job out of any tube that we're watching.
   *
   * @param timeoutSeconds The number of seconds to wait for a job to be ready for us. Specify null
   *                       to mean "indefinitely" or 0 to return immediately if no jobs are
   *                       available.
   * @return The reserved job. Never null.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  Job reserve(Integer timeoutSeconds) throws IOException;

  /**
   * Delete a job by ID.
   *
   * @param jobId The job to delete.
   * @return Whether the job was found.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  boolean delete(long jobId) throws IOException;

  /**
   * To release a job that was reserved. This puts it back into the ready queue (if delaySeconds is
   * 0) or the delayed queue (if not).
   *
   * @param jobId        The job to release.
   * @param priority     The new priority of the job, between 0 (most urgent) inclusive and 2**32
   *                     (least urgent) exclusive.
   * @param delaySeconds How many seconds to wait before delivering the job.
   * @return Whether the job was found.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  boolean release(long jobId, long priority, int delaySeconds) throws IOException;

  /**
   * Bury the job after being reserved. This is useful for jobs that cause errors and that should be
   * inspected before being issued (released) again.
   *
   * @param jobId    The job to bury.
   * @param priority The new priority of the job, between 0 (most urgent) inclusive and 2**32 (least
   *                 urgent) exclusive.
   * @return Whether the job was found.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  boolean bury(long jobId, long priority) throws IOException;

  /**
   * Touch a reserved job, to tell the server that you're still working on it and it shouldn't
   * expire it.
   *
   * @param jobId The job to touch.
   * @return Whether the job was found.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  boolean touch(long jobId) throws IOException;

  /**
   * Add the tube to the watch list. A subsequent "reserve" will pull from watched tubes.
   *
   * @param tubeName The tube to watch.
   * @return The number of tubes in the watch list (after this command).
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  int watch(String tubeName) throws IOException;

  /**
   * Remove a tube from the watch list. The client is not permitted to ignore the last tube they're
   * watching.
   *
   * @param tubeName The tube to ignore.
   * @return The number of tubes in the watch list (after this command), or -1 if the client tried
   * to remove the last tube.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  int ignore(String tubeName) throws IOException;

  /**
   * Fetches the specified job, but does not remove it from any queue.
   *
   * @param jobId The job to peek.
   * @return The job, or null if not found.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  Job peek(long jobId) throws IOException;

  /**
   * Fetches the next ready job, in other words, the job that reserve() would have returned.
   *
   * @return The next ready job, or null if there are none.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  Job peekReady() throws IOException;

  /**
   * Fetches the delayed job with the shortest delay left.
   *
   * @return The next delayed job, or null if there are none.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  Job peekDelayed() throws IOException;

  /**
   * Fetches the next job in the bury list.
   *
   * @return The next buried job, or null if there are none.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  Job peekBuried() throws IOException;

  /**
   * In the currently "used" tube, kicks buried jobs. If there are no buried jobs, kicks delayed
   * jobs.
   *
   * @param count The maximum number of jobs to kick.
   * @return The number of jobs that were kicked.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  int kick(int count) throws IOException;

  /*****************************************************************
   * Consumer methods stats-related ***************************************************************
   *
   * /** Get statistics about a job.
   *
   * @param jobId The job whose statistics you want.
   * @return A map with the following fields:
   *
   * <ul> <li>"id" is the job id</li> <li>"tube" is the name of the tube that contains this job</li>
   * <li>"state" is "ready" or "delayed" or "reserved" or "buried"</li> <li>"pri" is the priority
   * value set by the put, release, or bury commands.</li> <li>"age" is the time in seconds since
   * the put command that created this job.</li> <li>"time-left" is the number of seconds left until
   * the server puts this job into the ready queue. This number is only meaningful if the job is
   * reserved or delayed. If the job is reserved and this amount of time elapses before its state
   * changes, it is considered to have timed out.</li> <li>"timeouts" is the number of times this
   * job has timed out during a reservation.</li> <li>"releases" is the number of times a client has
   * released this job from a reservation.</li> <li>"buries" is the number of times this job has
   * been buried.</li> <li>"kicks" is the number of times this job has been kicked.</li> </ul>
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  Map<String, String> statsJob(long jobId) throws IOException;

  /**
   * Get statistics about a tube.
   *
   * @param tubeName The tube whose statistics you want.
   * @return A map with the following fields:
   *
   * <ul> <li>"name" is the tube's name.</li> <li>"current-jobs-urgent" is the number of ready jobs
   * with priority less than 1024 in this tube.</li> <li>"current-jobs-ready" is the number of jobs
   * in the ready queue in this tube.</li> <li>"current-jobs-reserved" is the number of jobs
   * reserved by all clients in this tube.</li> <li>"current-jobs-delayed" is the number of delayed
   * jobs in this tube.</li> <li>"current-jobs-buried" is the number of buried jobs in this
   * tube.</li> <li>"total-jobs" is the cumulative count of jobs created in this tube.</li>
   * <li>"current-waiting" is the number of open connections that have issued a reserve command
   * while watching this tube but not yet received a response.</li> </ul>
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  Map<String, String> statsTube(String tubeName) throws IOException;

  /**
   * Get statistics about the server.
   *
   * @return A map with the following fields:
   *
   * <ul> <li>"current-jobs-urgent" is the number of ready jobs with priority less than 1024.</li>
   * <li>"current-jobs-ready" is the number of jobs in the ready queue.</li>
   * <li>"current-jobs-reserved" is the number of jobs reserved by all clients.</li>
   * <li>"current-jobs-delayed" is the number of delayed jobs.</li> <li>"current-jobs-buried" is the
   * number of buried jobs.</li> <li>"cmd-put" is the cumulative number of put commands.</li>
   * <li>"cmd-peek" is the cumulative number of peek commands.</li> <li>"cmd-peek-ready" is the
   * cumulative number of peek-ready commands.</li> <li>"cmd-peek-delayed" is the cumulative number
   * of peek-delayed commands.</li> <li>"cmd-peek-buried" is the cumulative number of peek-buried
   * commands.</li> <li>"cmd-reserve" is the cumulative number of reserve commands.</li>
   * <li>"cmd-use" is the cumulative number of use commands.</li> <li>"cmd-watch" is the cumulative
   * number of watch commands.</li> <li>"cmd-ignore" is the cumulative number of ignore
   * commands.</li> <li>"cmd-delete" is the cumulative number of delete commands.</li>
   * <li>"cmd-release" is the cumulative number of release commands.</li> <li>"cmd-bury" is the
   * cumulative number of bury commands.</li> <li>"cmd-kick" is the cumulative number of kick
   * commands.</li> <li>"cmd-stats" is the cumulative number of stats commands.</li>
   * <li>"cmd-stats-job" is the cumulative number of stats-job commands.</li> <li>"cmd-stats-tube"
   * is the cumulative number of stats-tube commands.</li> <li>"cmd-list-tubes" is the cumulative
   * number of list-tubes commands.</li> <li>"cmd-list-tube-used" is the cumulative number of
   * list-tube-used commands.</li> <li>"cmd-list-tubes-watched" is the cumulative number of
   * list-tubes-watched commands.</li> <li>"job-timeouts" is the cumulative count of times a job has
   * timed out.</li> <li>"total-jobs" is the cumulative count of jobs created.</li>
   * <li>"max-job-size" is the maximum number of bytes in a job.</li> <li>"current-tubes" is the
   * number of currently-existing tubes.</li> <li>"current-connections" is the number of currently
   * open connections.</li> <li>"current-producers" is the number of open connections that have each
   * issued at least one put command.</li> <li>"current-workers" is the number of open connections
   * that have each issued at least one reserve command.</li> <li>"current-waiting" is the number of
   * open connections that have issued a reserve command but not yet received a response.</li>
   * <li>"total-connections" is the cumulative count of connections.</li> <li>"pid" is the process
   * id of the server.</li> <li>"version" is the version string of the server.</li>
   * <li>"rusage-utime" is the accumulated user CPU time of this process in seconds and
   * microseconds.</li> <li>"rusage-stime" is the accumulated system CPU time of this process in
   * seconds and microseconds.</li> <li>"uptime" is the number of seconds since this server started
   * running.</li> <li>"binlog-oldest-index" is the index of the oldest binlog file needed to store
   * the current jobs</li> <li>"binlog-current-index" is the index of the current binlog file being
   * written to. If binlog is not active this value will be 0</li> <li>"binlog-max-size" is the
   * maximum size in bytes a binlog file is allowed to get before a new binlog file is opened</li>
   * </ul>
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  Map<String, String> stats() throws IOException;

  /**
   * Fetch a list of all existing tubes.
   *
   * @return A list of all existing tubes.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  List<String> listTubes() throws IOException;

  /**
   * Fetch the tube currently being used by this client.
   *
   * @return The used tube.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  String listTubeUsed() throws IOException;

  /**
   * Fetch the list of tubes currently being watched by this client.
   *
   * @return A list of watched tubes.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  List<String> listTubesWatched() throws IOException;

  /**
   * Get the version of this beanstalk client.
   */
  String getClientVersion();

  /**
   * Get the version of the beanstalkd daemon.
   *
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  String getServerVersion() throws IOException;

  /**
   * Close underlying connection to beanstalkd.
   */
  void close();

  /**
   * Pause a tube. There's no documentation for this command in the protocol.
   *
   * @param tubeName The tube to pause.
   * @param pause    An integer number of seconds to wait before reserving any more jobs from the
   *                 queue.
   * @throws IOException        on network error.
   * @throws BeanstalkException on protocol error.
   */
  boolean pauseTube(String tubeName, int pause) throws IOException;
}
