/**
 *
 */
package org.apache.felix.atomos.maven.scrmock;

import org.apache.felix.scr.impl.logger.ScrLogger;
import org.apache.felix.scr.impl.manager.ScrConfiguration;
import org.osgi.framework.BundleContext;

public class EmptySCRLogger extends ScrLogger
{

    public EmptySCRLogger(ScrConfiguration config, BundleContext bundleContext)
    {
        super(config, bundleContext);

    }

    @Override
    public void close()
    {

    }

    @Override
    public boolean isLogEnabled(int level)
    {
        return false;
    }

    @Override
    public boolean log(int level, String pattern, Throwable ex, Object... arguments)
    {
        return true;
    }

    @Override
    public boolean log(int level, String message, Throwable ex)
    {
        return true;
    }

}
