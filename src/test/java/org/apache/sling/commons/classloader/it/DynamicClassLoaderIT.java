/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.commons.classloader.it;

import java.io.InputStream;
import java.net.URL;

import javax.inject.Inject;

import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(PaxExam.class)
public class DynamicClassLoaderIT extends ClassloaderTestSupport {

    private static String commonsOsgi = "mvn:org.apache.sling/org.apache.sling.commons.osgi/2.1.0";
    
    @Inject
    protected BundleContext bundleContext;

    protected ClassLoader dynamicClassLoader;

    protected ServiceReference<DynamicClassLoaderManager> classLoaderManagerReference;

    /**
     * Helper method to get a service of the given type
     */
	protected <T> T getService(Class<T> clazz) {
        
    	final ServiceReference<T> ref = bundleContext.getServiceReference(clazz);
    	assertNotNull("getService(" + clazz.getName() + ") must find ServiceReference", ref);
    	final T result = bundleContext.getService(ref);
    	assertNotNull("getService(" + clazz.getName() + ") must find service", result);
    	return result;
    }

    protected ClassLoader getDynamicClassLoader() {
        if ( classLoaderManagerReference == null || classLoaderManagerReference.getBundle() == null ) {
            dynamicClassLoader = null;
            classLoaderManagerReference = bundleContext.getServiceReference(DynamicClassLoaderManager.class);
        }
        if ( dynamicClassLoader == null && classLoaderManagerReference != null ) {
            final DynamicClassLoaderManager dclm = bundleContext.getService(classLoaderManagerReference);
            if ( dclm != null ) {
                dynamicClassLoader = dclm.getDynamicClassLoader();
            }
        }
        return dynamicClassLoader;
    }

    @ProbeBuilder
    public TestProbeBuilder extendProbe(TestProbeBuilder builder) {
        builder.setHeader(Constants.IMPORT_PACKAGE, "org.osgi.framework,org.apache.sling.commons.classloader,org.apache.sling.testing.paxexam");
        builder.setHeader(Constants.DYNAMICIMPORT_PACKAGE, "org.ops4j.pax.exam,org.junit,javax.inject,org.ops4j.pax.exam.options");
        builder.setHeader("Bundle-ManifestVersion", "2");
        return builder;
    }

    @Test
    public void testPackageAdminClassLoader() throws Exception {
        // check class loader
        assertNotNull(getDynamicClassLoader());

        final URL url = new URL(commonsOsgi);
        Bundle osgiBundle;
        try ( InputStream input = url.openStream() ) {
            osgiBundle = this.bundleContext.installBundle(commonsOsgi, input);
        }
        assertNotNull(osgiBundle);
        assertEquals(Bundle.INSTALLED, osgiBundle.getState());

        final String className = "org.apache.sling.commons.osgi.PropertiesUtil";

        // try to load class when bundle is in state install: should fail
        try {
            getDynamicClassLoader().loadClass(className);
            fail("Class should not be available");
        } catch (final ClassNotFoundException expected) {
            // expected
        }

        // force resolving of the bundle
        osgiBundle.getResource("/something");
        assertEquals(Bundle.RESOLVED, osgiBundle.getState());
        // try to load class when bundle is in state resolve: should fail
        try {
            getDynamicClassLoader().loadClass(className);
            fail("Class should not be available");
        } catch (final ClassNotFoundException expected) {
            // expected
        }

        // start bundle
        osgiBundle.start();
        assertEquals(Bundle.ACTIVE, osgiBundle.getState());
        // try to load class when bundle is in state activate: should work
        try {
            getDynamicClassLoader().loadClass(className);
        } catch (final ClassNotFoundException expected) {
            fail("Class should be available");
        }
    }
    
}