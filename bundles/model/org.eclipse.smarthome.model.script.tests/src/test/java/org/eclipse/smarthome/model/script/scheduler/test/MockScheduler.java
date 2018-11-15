/**
 * Copyright (c) 2014,2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.smarthome.model.script.scheduler.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;

/**
 * Mock version of the Quartz Scheduler.
 *
 * This is used to make the tests reliable, because
 * we don't need to wait for arbitrary lengths of time
 * for jobs to execute
 *
 * @author Jon Evans - initial contribution
 *
 */
public class MockScheduler extends AbstractScheduler {
    private final List<JobExecutionContext> currentlyExecutingJobs = new ArrayList<>();
    private final Map<Trigger, JobExecutionContext> jobs = new HashMap<>();
    private final Map<TriggerKey, OperableTrigger> rescheduledJobs = new HashMap<>();

    @Override
    public String getSchedulerName() throws SchedulerException {
        return "MockScheduler";
    }

    @Override
    public List<JobExecutionContext> getCurrentlyExecutingJobs() throws SchedulerException {
        return currentlyExecutingJobs;
    }

    @Override
    public boolean checkExists(JobKey jobKey) throws SchedulerException {
        for (JobExecutionContext context : jobs.values()) {
            if (jobKey.equals(context.getJobDetail().getKey())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Date scheduleJob(JobDetail jobDetail, Trigger trigger) throws SchedulerException {
        OperableTrigger oTrigger = (OperableTrigger) trigger;
        oTrigger.setNextFireTime(trigger.getStartTime());
        TriggerFiredBundle tfb = new TriggerFiredBundle(jobDetail, // job
                oTrigger, // trigger
                null, // cal
                false, // jobIsRecovering
                null, // fireTime
                null, // scheduledFireTime
                null, // prevFireTime
                null // nextFireTime
        );
        JobExecutionContextImpl jec = new JobExecutionContextImpl(this, tfb, null);
        jobs.put(oTrigger, jec);
        return trigger.getStartTime();
    }

    @Override
    public Date rescheduleJob(TriggerKey triggerKey, Trigger newTrigger) throws SchedulerException {
        for (Trigger trigger : jobs.keySet()) {
            if (triggerKey.equals(trigger.getKey())) {
                OperableTrigger oTrigger = (OperableTrigger) newTrigger;
                oTrigger.setNextFireTime(oTrigger.getStartTime());
                rescheduledJobs.put(triggerKey, oTrigger);
                return new Date();
            }
        }
        return null;
    }

    @Override
    public List<? extends Trigger> getTriggersOfJob(JobKey jobKey) throws SchedulerException {
        for (Entry<Trigger, JobExecutionContext> entry : jobs.entrySet()) {
            JobExecutionContext jec = entry.getValue();
            if (jobKey.equals(jec.getJobDetail().getKey())) {
                return Collections.singletonList(entry.getKey());
            }
        }
        return Collections.emptyList();
    }

    /**
     * "Run" all of the jobs in the scheduler.
     *
     * NB this is a mock class. We ignore the time that the jobs are
     * actually scheduled for, and just run them all.
     *
     * @throws JobExecutionException
     * @throws IllegalAccessException
     * @throws InstantiationException
     *
     */
    public void run() throws JobExecutionException, InstantiationException, IllegalAccessException {
        for (Entry<Trigger, JobExecutionContext> entry : jobs.entrySet()) {
            JobExecutionContext context = entry.getValue();
            try {
                currentlyExecutingJobs.add(context);
                Job job = context.getJobDetail().getJobClass().newInstance();
                job.execute(context);
            } finally {
                currentlyExecutingJobs.remove(context);
                jobs.remove(entry.getKey());
                Trigger newTrigger = rescheduledJobs.remove(context.getTrigger().getKey());
                if (newTrigger != null) {
                    jobs.put(newTrigger, context);
                }
            }
        }
    }

    public int getPendingJobCount() {
        return jobs.size();
    }

    public void reset() {
        currentlyExecutingJobs.clear();
        jobs.clear();
        rescheduledJobs.clear();
    }
}
