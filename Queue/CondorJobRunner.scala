//Modified by Daniel Bottomly
/*
* Copyright (c) 2012 The Broad Institute
*
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
*
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.broadinstitute.gatk.queue.engine.condor

import java.util.Collections

import org.broadinstitute.gatk.queue.QException
import org.broadinstitute.gatk.queue.engine.RunnerStatus
import org.broadinstitute.gatk.queue.engine.drmaa.DrmaaJobRunner
import org.broadinstitute.gatk.queue.function.CommandLineFunction
import org.broadinstitute.gatk.queue.util.{Retry, Logging}
import org.ggf.drmaa.{DrmaaException, JobTemplate, Session}

/**
 * Runs jobs on an HTCondor conpute cluster
*/
class CondorJobRunner(session: Session, function: CommandLineFunction) extends DrmaaJobRunner(session, function) with Logging {
  // Grid Engine disallows certain characters from being in job names.
  // This replaces all illegal characters with underscores
  protected override val jobNameFilter = """[\s/:,@\\*?]"""
  protected override val minRunnerPriority = -1023
  protected override val maxRunnerPriority = 0

  override protected def functionNativeSpec = {

    //should look like:
    //"image_size=65536\nrank=Memory\n+department=\"chemistry\""

    // Force the remote environment to inherit local environment settings
    var nativeSpec: String = "getenv=True\n"

    // If the resident set size limit is defined specify the memory limit
    if (function.residentLimit.isDefined)
      nativeSpec += "request_memory = %dM\n".format(function.residentLimit.map(_ * 1024).get.ceil.toInt)

    // If more than 1 core is requested, set the proper request
    // if we aren't being jerks and just stealing cores (previous behavior)

    if ( function.nCoresRequest.getOrElse(1) > 1 ) {
      if ( function.qSettings.dontRequestMultipleCores )
        logger.warn("Sending multicore job %s to farm without requesting appropriate number of cores (%d)".format(
          function.shortDescription, function.nCoresRequest.get))
      else
        //nativeSpec += " -pe %s %d".format(function.qSettings.parallelEnvironmentName, function.nCoresRequest.get)
        nativeSpec += "request_cpus=%d\n".format(function.nCoresRequest.get)
    }

    //as there is something haywire with the drmaa output will add stdout and err here
    nativeSpec += "output=%s\n".format(function.jobOutputFile.getPath)

    if (function.jobErrorFile != null) {
      nativeSpec += "error=%s\n".format(function.jobErrorFile.getPath)

    }else{
      nativeSpec += "error=%s\n".format(function.jobOutputFile.getPath.replace(".out", ".err"))
    }

    logger.debug("Native spec is: %s".format(nativeSpec))
    (nativeSpec + " " + super.functionNativeSpec).trim()
  }

  override def start() {
    session.synchronized {
      val drmaaJob: JobTemplate = session.createJobTemplate

      drmaaJob.setJobName(function.jobRunnerJobName.take(jobNameLength).replaceAll(jobNameFilter, "_"))

      // Set the current working directory
      drmaaJob.setWorkingDirectory(function.commandDirectory.getPath)

      drmaaJob.setNativeSpecification(functionNativeSpec)

      // Instead of running the function.commandLine, run "sh <jobScript>"
      drmaaJob.setRemoteCommand("/bin/sh")
      drmaaJob.setArgs(Collections.singletonList(jobScript.toString))

      // Allow advanced users to update the request via QFunction.updateJobRun()
      updateJobRun(drmaaJob)

      updateStatus(RunnerStatus.RUNNING)

      // Start the job and store the id so it can be killed in tryStop
      try {
        Retry.attempt(() => {
          try {
            jobId = session.runJob(drmaaJob)
          } catch {
            case de: DrmaaException => throw new QException("Unable to submit job: " + de.getLocalizedMessage)
          }
        }, 1, 5, 10)
      } finally {
        // Prevent memory leaks
        session.deleteJobTemplate(drmaaJob)
      }
      logger.info("Submitted job id: " + jobId)
    }
  }
}
