package com.felix.aigate.application.service;

import com.felix.aigate.application.entity.Application;
import com.felix.aigate.application.mapper.ApplicationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationMapper applicationMapper;

    public Application createApplication(String name, Long teamId) {
        Application application = new Application();
        application.setName(name);
        application.setTeamId(teamId);

        applicationMapper.insert(application);

        return applicationMapper.selectById(application.getId());
    }

    public Application getApplicationById(Long id) {
        return applicationMapper.selectById(id);
    }

    public List<Application> listApplications() {
        return applicationMapper.selectList(null);
    }

    public Application updateApplication(Long id, String name, Long teamId) {
        Application application = applicationMapper.selectById(id);

        application.setName(name);
        application.setTeamId(teamId);

        applicationMapper.updateById(application);

        return applicationMapper.selectById(id);
    }

    public void deleteApplication(Long id) {
        applicationMapper.deleteById(id);
    }
}