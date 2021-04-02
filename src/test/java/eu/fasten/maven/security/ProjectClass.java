package eu.fasten.maven.security;

import java.io.IOException;

import org.jboss.resteasy.core.interception.ResponseContainerRequestContext;
import org.jboss.resteasy.plugins.interceptors.CorsFilter;

public class ProjectClass
{
    public static void m() throws IOException
    {
        CorsFilter filter = new CorsFilter();
        ResponseContainerRequestContext context = new ResponseContainerRequestContext(null);
        filter.filter(context);
    }
}
