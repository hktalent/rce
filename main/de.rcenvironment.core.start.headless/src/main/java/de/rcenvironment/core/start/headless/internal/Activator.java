/*
 * Copyright (C) 2006-2016 DLR, Germany
 * 
 * All rights reserved
 * 
 * http://www.rcenvironment.de/
 */

package de.rcenvironment.core.start.headless.internal;

import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import de.rcenvironment.core.start.common.Instance;
import de.rcenvironment.core.start.headless.HeadlessInstanceRunner;

/**
 * Simple activator to "inject" the headless instance runner.
 * 
 * @author Robert Mischke
 */
public class Activator implements BundleActivator {

    @Override
    public void start(BundleContext arg0) throws Exception {
        LogFactory.getLog(getClass()).debug("Initializing headless runner");
        Instance.setInstanceRunner(new HeadlessInstanceRunner());
    }

    @Override
    public void stop(BundleContext arg0) throws Exception {}

}
