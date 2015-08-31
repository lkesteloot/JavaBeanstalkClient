package com.teamten.beanstalk;

/*

Copyright 2009-2010 Robert Tykulsker

This file is part of JavaBeanstalkCLient.

JavaBeanstalkCLient is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version, or alternatively, the BSD license supplied
with this project in the file "BSD-LICENSE".

JavaBeanstalkCLient is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with JavaBeanstalkCLient.  If not, see <http://www.gnu.org/licenses/>.

*/

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

public class BeanstalkClientImplTest {

  private static final String TEST_HOST = "lx00943.starbucks.net";
  private static final int TEST_PORT = 11300;
  private static final int TEST_TIMEOUT_SECONDS = 2;
  private static final String DEFAULT_TUBE = "default";

  // May want to adjust this to 2 for local or 100 for remote
  private static final int AVERAGE_PUT_PERFORMANCE_MILLIS = 100;

  private BeanstalkClient client;

  @Before
  public void setUp() throws IOException {
    client = new BeanstalkClientImpl(TEST_HOST, TEST_PORT);
  }

  /**
   * ignore all currently watched tubes, returned in ret[0] watch a new tube, returned in ret[1]
   */
  private Object[] pushWatchedTubes(final BeanstalkClient client) throws IOException {
    final Object[] tubeNames = new Object[2];
    final List<String> list = client.listTubesWatched();

    final String newTubeName = "tube-" + UUID.randomUUID().toString();
    client.watch(newTubeName);

    for (final String existingTubeName : list) {
      client.ignore(existingTubeName);
    }

    tubeNames[0] = list;
    tubeNames[1] = newTubeName;

    return tubeNames;
  }

  @SuppressWarnings("unchecked")
  private void popWatchedTubes(final BeanstalkClient client, final Object[] tubeNames)
      throws IOException {
    for (final String tubeName : (List<String>) tubeNames[0]) {
      client.watch(tubeName);
    }

    client.ignore((String) tubeNames[1]);
  }

  @Test
  public void testGetServerVersion() throws IOException {
    final String serverVersion = client.getServerVersion();
    assertNotNull(serverVersion);

    final String[] tokens = serverVersion.split("\\.");

    if (tokens.length >= 2) {
      final int majorVersion = Integer.parseInt(tokens[0]);
      final int minorVersion = Integer.parseInt(tokens[1]);
      assertTrue(majorVersion >= 1);
      assertTrue(minorVersion >= 4);
    } else {
      fail("server version has bad format: " + serverVersion);
    }
  }

  @Test
  public void testBinaryData() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);

    final byte[] srcBytes = new byte[256];
    for (int i = 0; i < srcBytes.length; ++i) {
      srcBytes[i] = (byte) i;
    }

    // producer
    client.useTube((String) tubeNames[1]);

    final long jobId = client.put(65536, 0, 120, srcBytes);
    assertTrue(jobId > 0);

    // consumer
    final Job job = client.reserve(null);
    assertNotNull(job);

    long newJobId = job.getJobId();
    assertEquals(jobId, newJobId);

    // verify bytes
    final byte[] dstBytes = job.getData();
    assertEquals(srcBytes.length, dstBytes.length);

    for (int i = 0; i < srcBytes.length; ++i) {
      assertEquals(srcBytes[i], dstBytes[i]);
    }

    client.delete(job.getJobId());

    popWatchedTubes(client, tubeNames);
  }

  @Test
  public void testUseTube() throws IOException {
    client.useTube("foobar");
  }

  @Test(expected = BeanstalkException.class)
  public void testUseTubeWithHash() throws IOException {
    client.useTube("foobar#");
  }

  @Test
  public void testUseTubeWithUnderscore() throws IOException {
    // underscores are valid in tube names >= beanstalk 1.4.4.
    try {
      client.useTube("foobar_");
    } catch (final BeanstalkException e) {
      fail(e.getMessage());
    }
  }

  @Test(expected = BeanstalkException.class)
  public void testUseTubeWithHyphen() throws IOException {
    // per pashields http://github.com/pashields/JavaBeanstalkClient.git
    // Names cannot start with hyphen
    client.useTube("-foobar");
  }

  @Test
  public void testPut() throws IOException {
    final long jobId = client.put(65536, 0, 120, "testPut".getBytes());
    assertTrue(jobId > 0);
    client.delete(jobId);
  }

  @Test(expected = BeanstalkException.class)
  public void testPutNegativePriority() throws IOException {
    client.put(-1, 0, 120, "testPutNegativePriority".getBytes());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testPutInvalidPriority() throws IOException {
    client.put(Long.MAX_VALUE, 0, 120, "testPutHugePriority".getBytes());
  }

  @Test(expected = NullPointerException.class)
  public void testPutNullData() throws IOException {
    client.put(65536, 0, 120, null);
  }

  @Test
  public void testReserve() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testReserve";

    // producer
    client.useTube((String) tubeNames[1]);
    final long jobId = client.put(65536, 0, 120, srcString.getBytes());
    assertTrue(jobId > 0);

    // consumer
    final Job job = client.reserve(null);
    assertNotNull(job);

    final long newJobId = job.getJobId();
    assertEquals(jobId, newJobId);

    final String dstString = new String(job.getData());
    assertEquals(srcString, dstString);

    client.delete(job.getJobId());
    popWatchedTubes(client, tubeNames);
  }

  @Test
  public void testReserveWithValidTimeout() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testReserveWithValidTimeout";

    // producer
    client.useTube((String) tubeNames[1]);
    final long putMillis = System.currentTimeMillis();
    final long jobId = client.put(65536, TEST_TIMEOUT_SECONDS, 120, srcString.getBytes());
    assertTrue(jobId > 0);

    // consumer
    final Job job = client.reserve(TEST_TIMEOUT_SECONDS);
    assertNotNull(job);

    final long getMillis = System.currentTimeMillis();
    final long newJobId = job.getJobId();
    assertEquals(jobId, newJobId);

    final String dstString = new String(job.getData());
    assertEquals(srcString, dstString);

    final long deltaSeconds = (getMillis - putMillis) / 1000;
    assertTrue(deltaSeconds >= TEST_TIMEOUT_SECONDS);

    client.delete(job.getJobId());
  }

  @Test(expected = BeanstalkException.class)
  public void testReserveWithInvalidTimeout() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testReserveWithInvalidTimeout";

    client.useTube((String) tubeNames[1]);

    // now try to achieve a TIMED_OUT
    assertTrue(client.put(65536, 2 * TEST_TIMEOUT_SECONDS, 120, srcString.getBytes()) > 0);

    client.reserve(TEST_TIMEOUT_SECONDS);

//    popWatchedTubes(client, tubeNames);
  }

  @Test
  public void testDelete() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testDelete";

    client.useTube((String) tubeNames[1]);

    final long jobId = client.put(65536, 0, 120, srcString.getBytes());
    assertTrue(jobId > 0);

    final Job job = client.reserve(null);
    assertNotNull(job);

    // try to delete twice
    assertTrue(client.delete(job.getJobId()));
    assertFalse(client.delete(job.getJobId()));

    popWatchedTubes(client, tubeNames);
  }

  @Test
  public void testRelease() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testReserveWithTimeout";

    client.useTube((String) tubeNames[1]);

    // producer
    final long jobId = client.put(65536, TEST_TIMEOUT_SECONDS, 120, srcString.getBytes());
    assertTrue(jobId > 0);

    // not found
    assertFalse(client.release(jobId, 65536, 0));

    {
      final Job job = client.reserve(null);
      assertNotNull(job);
      assertEquals(jobId, job.getJobId());

      // quick release
      assertTrue(client.release(jobId, 65536, 0));
    }

    {
      final Job job = client.reserve(null);
      assertNotNull(job);
      assertEquals(jobId, job.getJobId());
      assertTrue(client.delete(jobId));
      assertFalse(client.release(jobId, 65536, 0));
    }

    popWatchedTubes(client, tubeNames);
  }

  @Test
  public void testBuryKick() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testBuryKick";

    // nothing to bury
    assertFalse(client.bury(0, 65536));

    client.useTube((String) tubeNames[1]);

    // producer
    final long jobId = client.put(65536, 0, 120, srcString.getBytes());
    assertTrue(jobId > 0);

    // we haven't reserved, so we can't bury
    assertFalse(client.bury(jobId, 65536));

    // we can bury
    {
      final Job job = client.reserve(0);
      assertNotNull(job);
      assertTrue(client.bury(jobId, 65536));
    }

    // nothing to reserve
    try {
      client.reserve(0);
      fail("No TIMED_OUT");
    } catch (final BeanstalkException e) {
      assertEquals("TIMED_OUT", e.getMessage());
    }

    // kick nothing
    {
      final int count = client.kick(0);
      assertEquals(0, count);

      try {
        client.reserve(0);
        fail("No TIMED_OUT");
      } catch (final BeanstalkException e) {
        assertEquals("TIMED_OUT", e.getMessage());
      }
    }

    // kick something
    {
      final int count = client.kick(1);
      assertEquals(1, count);

      final Job job = client.reserve(0);
      assertNotNull(job);
      assertEquals(jobId, job.getJobId());
      assertEquals(srcString, new String(job.getData()));
    }

    client.delete(jobId);

    popWatchedTubes(client, tubeNames);
  }

  @Test
  public void testTouch() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testTouch";

    // nothing to touch
    assertFalse(client.touch(0));

    client.useTube((String) tubeNames[1]);

    // producer
    final long jobId = client.put(65536, 0, TEST_TIMEOUT_SECONDS, srcString.getBytes());
    assertTrue(jobId > 0);

    // we haven't reserved, so we can't touch
    assertFalse(client.touch(jobId));

    // reserve the job
    assertNotNull(client.reserve(null));

    // try to reserve another job
    try {
      client.reserve(2 * TEST_TIMEOUT_SECONDS);
      fail("expected DEADLINE_SOON");
    } catch (final BeanstalkException be) {
      assertEquals("DEADLINE_SOON", be.getMessage());
      assertTrue(client.touch(jobId));
    } catch (Exception e) {
      fail("caught exception: " + e.getMessage());
    }

    client.delete(jobId);

    popWatchedTubes(client, tubeNames);
  }

  @Test
  public void testListTubeUsed() throws IOException {
    final String s = client.listTubeUsed();
    assertNotNull(s);
  }

  @Test
  public void testListTubes() throws IOException {
    final List<String> list = client.listTubes();
    assertNotNull(list);
  }

  @Test
  public void testListTubesWatched() throws IOException {
    List<String> list = client.listTubesWatched();
    assertNotNull(list);

    final int initialWatchCount = list.size();
    assertTrue(initialWatchCount >= 1);

    final String tubeName = "tube-" + UUID.randomUUID().toString();
    int watchCount = client.watch(tubeName);
    assertEquals(initialWatchCount + 1, watchCount);

    list = client.listTubesWatched();
    assertNotNull(list);
    assertEquals(watchCount, list.size());
    assertTrue(list.contains(tubeName));

    watchCount = client.ignore(tubeName);
    assertEquals(initialWatchCount, watchCount);

    list = client.listTubesWatched();
    assertNotNull(list);
    assertEquals(initialWatchCount, list.size());
    assertFalse(list.contains(tubeName));
  }

  @Test
  public void testStats() throws IOException {
    final Map<String, String> map = client.stats();
    assertNotNull(map);
  }

  @Test(expected = BeanstalkException.class)
  public void testStatsNullTube() throws IOException {
    client.statsTube(null);
  }

  @Test(expected = BeanstalkException.class)
  public void testStatsRandomTube() throws IOException {
    client.statsTube("tube-" + UUID.randomUUID().toString());
  }

  @Test
  public void testStatsTube() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testStatsTube";

    client.useTube((String) tubeNames[1]);

    // producer
    final long jobId = client.put(65536, 0, 120, srcString.getBytes());
    assertTrue(jobId > 0);

    final Job job = client.reserve(null);
    assertNotNull(job);

    client.delete(jobId);

    final Map<String, String> map = client.statsTube((String) tubeNames[1]);
    assertNotNull(map);

    popWatchedTubes(client, tubeNames);
  }

  @Test(expected = BeanstalkException.class)
  public void testStatsInvalidJob() throws IOException {
    client.statsJob(0);
  }

  @Test
  public void testStatsJob() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testStatsJob";

    client.useTube((String) tubeNames[1]);

    // producer
    final long jobId = client.put(65536, 0, 120, srcString.getBytes());
    assertTrue(jobId > 0);

    final Job job = client.reserve(null);
    assertNotNull(job);

    final Map<String, String> map = client.statsJob(jobId);
    assertNotNull(map);

    client.delete(jobId);

    popWatchedTubes(client, tubeNames);
  }

  @Test(expected = BeanstalkException.class)
  public void testPeekNegative() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    client.useTube((String) tubeNames[1]);
    client.peek(-1);
  }

  @Test(expected = BeanstalkException.class)
  public void testPeekZero() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    client.useTube((String) tubeNames[1]);
    client.peek(0);
  }

  @Test
  public void testPeek() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testPeek-";

    client.useTube((String) tubeNames[1]);

    final int nJobs = 3;
    final long[] jobIds = new long[nJobs];

    // producer
    for (int i = 0; i < nJobs; ++i) {
      final long jobId = client.put(65536, 0, 120, (srcString + i).getBytes());
      assertTrue(jobId > 0);
      jobIds[i] = jobId;
    }

    // peek 'em once
    for (int i = 0; i < nJobs; ++i) {
      final Job job = client.peek(jobIds[i]);
      assertNotNull(job);
      assertEquals(jobIds[i], job.getJobId());
    }

    // peek 'em again
    for (int i = 0; i < nJobs; ++i) {
      final Job job = client.peek(jobIds[i]);
      assertNotNull(job);
      assertEquals(jobIds[i], job.getJobId());
    }

    // reserve and delete
    for (int i = 0; i < nJobs; ++i) {
      final Job job = client.reserve(null);
      assertNotNull(job);
      assertEquals(jobIds[i], job.getJobId());
      client.delete(job.getJobId());
    }

    // peek one last time
    for (int i = 0; i < nJobs; ++i) {
      try {
        client.peek(jobIds[i]);
        fail("No NOT_FOUND");
      } catch (final BeanstalkException e) {
        assertEquals("NOT_FOUND", e.getMessage());
      }
    }

    popWatchedTubes(client, tubeNames);
  }

  @Test
  public void testReady() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testPeekReady-";

    client.useTube((String) tubeNames[1]);

    final int nJobs = 3;
    final long[] jobIds = new long[nJobs];

    // producer
    for (int i = 0; i < nJobs; ++i) {
      final long jobId = client.put(65536, 0, 120, (srcString + i).getBytes());
      assertTrue(jobId > 0);
      jobIds[i] = jobId;
    }

    // peek 'em once
    for (int i = 0; i < nJobs; ++i) {
      final Job job = client.peekReady();
      assertNotNull(job);
      assertEquals(jobIds[0], job.getJobId());
    }

    // reserve and delete
    for (int i = 0; i < nJobs; ++i) {
      final Job job = client.reserve(null);
      assertNotNull(job);
      assertEquals(jobIds[i], job.getJobId());
      client.delete(job.getJobId());
    }

    // peek one last time
    for (int i = 0; i < nJobs; ++i) {
      try {
        client.peekReady();
        fail("No NOT_FOUND");
      } catch (final BeanstalkException e) {
        assertEquals("NOT_FOUND", e.getMessage());
      }
    }

    popWatchedTubes(client, tubeNames);
  }

  @Test
  public void testDelayed() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testPeekDelay";

    client.useTube((String) tubeNames[1]);

    final int delaySeconds = 2;

    client.useTube((String) tubeNames[1]);

    // producer
    // note we adjust delay
    final long jobId = client.put(65536, delaySeconds, 120, srcString.getBytes());
    assertTrue(jobId > 0);

    {
      // peekDelayed
      final Job job = client.peekDelayed();
      assertNotNull(job);
      assertEquals(jobId, job.getJobId());
    }

    try {
      Thread.sleep(delaySeconds * 1000);
    } catch (final Exception e) {
      // no-op
    }

    {
      // reserve and delete
      final Job job = client.reserve(null);
      assertNotNull(job);
      assertEquals(jobId, job.getJobId());
      client.delete(job.getJobId());
    }

    try {
      // peek one last time
      client.peekDelayed();
      fail("No NOT_FOUND");
    } catch (final BeanstalkException e) {
      assertEquals("NOT_FOUND", e.getMessage());
    }

    popWatchedTubes(client, tubeNames);
  }

  @Test
  public void testBuried() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testPeekBuried";

    client.useTube((String) tubeNames[1]);

    try {
      // peekBuried
      client.peekBuried();
      fail("No NOT_FOUND");
    } catch (final BeanstalkException e) {
      assertEquals("NOT_FOUND", e.getMessage());
    }

    // producer
    final long jobId = client.put(65536, 0, 120, srcString.getBytes());
    assertTrue(jobId > 0);

    try {
      // peekBuried
      final Job job = client.peekBuried();
      fail("No NOT_FOUND");
    } catch (final BeanstalkException e) {
      assertEquals("NOT_FOUND", e.getMessage());
    }

    {
      // reserve and bury
      final Job job = client.reserve(null);
      assertNotNull(job);
      assertEquals(jobId, job.getJobId());
      client.bury(job.getJobId(), 65536);
    }

    {
      // peekBuried
      final Job job = client.peekBuried();
      assertNotNull(job);
      assertEquals(jobId, job.getJobId());
    }

    // delete
    client.delete(jobId);

    try {
      // peekBuried
      client.peekBuried();
      fail("No NOT_FOUND");
    } catch (final BeanstalkException e) {
      assertEquals("NOT_FOUND", e.getMessage());
    }

    popWatchedTubes(client, tubeNames);
  }

  @Test
  public void testClose() throws IOException {
    final String s = client.listTubeUsed();
    assertNotNull(s);

    client.close();

    try {
      client.listTubeUsed();
      fail("didn't throw expected exception");
    } catch (final IOException be) {
      assertEquals("Socket is closed", be.getMessage());
    } catch (final Exception e) {
      fail("caught exception: " + e.getMessage());
    }

    // close again
    client.close();

    try {
      client.listTubeUsed();
      fail("didn't throw expected exception");
    } catch (final IOException be) {
      assertEquals("Socket is closed", be.getMessage());
    } catch (Exception e) {
      fail("caught exception: " + e.getMessage());
    }
  }

  @Test
  public void testUseBlockIO() throws IOException {
    final String remoteHost = TEST_HOST;
    final int nIterations = 100;

    for (int i = 0; i < nIterations; ++i) {
      final BeanstalkClient client = new BeanstalkClientImpl(remoteHost, TEST_PORT);
      final Object[] tubeNames = pushWatchedTubes(client);
      final String srcString = "testUseBlockIO";

      client.useTube((String) tubeNames[1]);

      // producer
      final long jobId = client.put(65536, 0, 120, srcString.getBytes());
      assertTrue(jobId > 0);

      // consumer
      final Job job = client.reserve(null);
      assertNotNull(job);

      final long newJobId = job.getJobId();
      assertEquals(jobId, newJobId);

      final String dstString = new String(job.getData());
      assertEquals(srcString, dstString);

      client.delete(job.getJobId());

      popWatchedTubes(client, tubeNames);

      client.close();
    }
  }

  @Test(expected = NullPointerException.class)
  public void testIgnoreNull() throws IOException {
    client.ignore(null);
  }

  @Test(expected = NullPointerException.class)
  public void testUseTubeNull() throws IOException {
    client.useTube(null);
  }

  @Test(expected = NullPointerException.class)
  public void testWatchNull() throws IOException {
    client.watch(null);
  }

  @Test
  public void testPutPerformance() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);

    client.useTube((String) tubeNames[1]);

    final byte[] bytes = "testPutPerformance".getBytes();
    final int nIterations = 10;
    long sumMillis = 0;

    for (int i = 0; i < nIterations; ++i) {
      final long startMillis = System.currentTimeMillis();
      client.put(0, 0, 120, bytes);
      sumMillis += (System.currentTimeMillis() - startMillis);
    }

    final long averageTime = sumMillis / nIterations;
    System.out.println("average: " + averageTime);

    assertTrue((sumMillis / nIterations) <= AVERAGE_PUT_PERFORMANCE_MILLIS);
  }

  @Test
  public void testIgnoreDefaultTube() throws IOException {
    final List<String> tubeNames = client.listTubesWatched();
    assertEquals(1, tubeNames.size());
    assertEquals(DEFAULT_TUBE, tubeNames.get(0));
    assertEquals(-1, client.ignore(DEFAULT_TUBE));
  }

  @Test
  public void testPauseTube() throws IOException {
    final Object[] tubeNames = pushWatchedTubes(client);
    final String srcString = "testPauseTube";

    client.useTube((String) tubeNames[1]);

    final int delaySeconds = 0;
    final int tubeDelay = 3;

    // producer
    //now pause the tube
    client.pauseTube((String) tubeNames[1], tubeDelay);

    // note we adjust delay
    final long jobId = client.put(65536, delaySeconds, 120, srcString.getBytes());
    assertTrue(jobId > 0);

    {
      // peekReady (but we still can't reserve because of tube delay)
      final Job job = client.peekReady();
      assertNotNull(job);
      assertEquals(jobId, job.getJobId());
    }

    try {
      //reserve with timeout a little less than tube pause time
      client.reserve(tubeDelay - 1);
      fail("No TIMED_OUT");
    } catch (final BeanstalkException e) {
      assertEquals("TIMED_OUT", e.getMessage());
    }

    {
      //now wait for tube to become un-paused
      final Job job = client.reserve(2);
      assertNotNull(job);
      assertEquals(jobId, job.getJobId());
    }

    popWatchedTubes(client, tubeNames);
  }
}
