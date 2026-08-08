package com.savelieva.jobdashboard;

import com.savelieva.jobdashboard.config.Dotenv;
import com.savelieva.jobdashboard.config.SearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SearchProperties.class)
public class JobDashboardApplication {

    public static void main(String[] args) {
        Dotenv.load();   // DASHBOARD_ROOT lives in .env, outside the repository
        SpringApplication.run(JobDashboardApplication.class, args);
    }
}
