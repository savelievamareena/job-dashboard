package com.savelieva.jobdashboard;

import com.savelieva.jobdashboard.config.CvProperties;
import com.savelieva.jobdashboard.config.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CvProperties.class)
public class JobDashboardApplication {

    public static void main(String[] args) {
        Dotenv.load();   // the database credentials live in .env, outside the repository
        SpringApplication.run(JobDashboardApplication.class, args);
    }
}
