package org.molgenis.coding;

import java.io.IOException;
import java.util.List;

import org.molgenis.coding.backup.BackupCodesInState;
import org.molgenis.coding.elasticsearch.CodingState;
import org.molgenis.coding.elasticsearch.SearchService;
import org.molgenis.coding.ngram.NGramService;
import org.molgenis.coding.util.DutchNGramAlgorithm;
import org.molgenis.coding.util.ProcessVariableUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;
import org.springframework.web.servlet.view.freemarker.FreeMarkerViewResolver;

@SuppressWarnings("deprecation")
@Configuration
@EnableWebMvc
@EnableAsync
@EnableScheduling
@ComponentScan("org.molgenis.coding")
public class WebAppConfig extends WebMvcConfigurerAdapter
{
	@Autowired
	private CodingState codingState;

	@Autowired
	private SearchService elasticSearchImp;

	@Autowired
	private NGramService nGramService;

	@Autowired
	private BackupCodesInState backupCodesInState;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry)
	{
		registry.addResourceHandler("/css/**").addResourceLocations("/css/", "classpath:/css/").setCachePeriod(3600);
		registry.addResourceHandler("/img/**").addResourceLocations("/img/", "classpath:/img/").setCachePeriod(3600);
		registry.addResourceHandler("/js/**").addResourceLocations("/js/", "classpath:/js/").setCachePeriod(3600);
	}

	@Override
	public void configureMessageConverters(List<HttpMessageConverter<?>> converters)
	{
		converters.add(new ResourceHttpMessageConverter());
		converters.add(new Jaxb2RootElementHttpMessageConverter());
		converters.add(new MappingJacksonHttpMessageConverter());
	}

	@Bean
	public MultipartResolver multipartResolver()
	{
		return new StandardServletMultipartResolver();
	}

	@Bean
	public CodingState codingState()
	{
		return new CodingState();
	}

	@Bean
	public BackupCodesInState backupCodesInState()
	{
		return new BackupCodesInState(elasticSearchImp, codingState);
	}

	@Bean
	public ProcessVariableUtil processVariableUtil()
	{
		return new ProcessVariableUtil(elasticSearchImp, codingState, nGramService);
	}

	/**
	 * Enable spring freemarker viewresolver. All freemarker template names
	 * should end with '.ftl'
	 */
	@Bean
	public ViewResolver viewResolver()
	{
		FreeMarkerViewResolver resolver = new FreeMarkerViewResolver();
		resolver.setCache(true);
		resolver.setSuffix(".ftl");

		return resolver;
	}

	/**
	 * Configure freemarker. All freemarker templates should be on the classpath
	 * in a package called 'templates'
	 */
	@Bean
	public FreeMarkerConfigurer freeMarkerConfigurer()
	{
		FreeMarkerConfigurer result = new FreeMarkerConfigurer();
		result.setPreferFileSystemAccess(false);
		result.setTemplateLoaderPath("classpath:/templates/");

		return result;
	}

	@Bean
	public DutchNGramAlgorithm dutchNGramAlgorithm()
	{
		return new DutchNGramAlgorithm();
	}

	@Bean
	public NGramService nGramService()
	{
		return new NGramService(dutchNGramAlgorithm());
	}

	@Scheduled(cron = "0 0 4 * * ?")
	public void indexUnfinishedResult() throws IOException
	{
		backupCodesInState.index();
	}
}