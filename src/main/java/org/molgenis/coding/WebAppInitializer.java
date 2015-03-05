package org.molgenis.coding;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration.Dynamic;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * This class replaces the traditional 'web.xml'
 * 
 */
public class WebAppInitializer implements WebApplicationInitializer
{
	private final static int maxFileSize = 32;

	@Override
	public void onStartup(ServletContext servletContext) throws ServletException
	{
		AnnotationConfigWebApplicationContext ctx = new AnnotationConfigWebApplicationContext();
		ctx.register(WebAppConfig.class);

		// spring
		Dynamic dispatcherServlet = servletContext.addServlet("dispatcher", new DispatcherServlet(ctx));
		dispatcherServlet.setLoadOnStartup(1);
		dispatcherServlet.addMapping("/*");
		final int maxSize = maxFileSize * 1024 * 1024;
		dispatcherServlet.setMultipartConfig(new MultipartConfigElement(null, maxSize, maxSize, maxSize));
		dispatcherServlet.setInitParameter("dispatchOptionsRequest", "true");

		Logger.getRootLogger().setLevel(Level.INFO);
	}

}
