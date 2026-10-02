package com.felix.aigate.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.felix.aigate.application.entity.Application;
import com.felix.aigate.application.mapper.ApplicationMapper;
import com.felix.aigate.common.exception.ConflictException;
import com.felix.aigate.team.mapper.TeamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.felix.aigate.common.exception.ResourceNotFoundException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationMapper applicationMapper;
    private final TeamMapper teamMapper;

    public Application createApplication(String name, Long teamId) {

        ensureTeamExists(teamId);
        ensureApplicationNameAvailable(name, null);

        Application application = new Application();
        application.setName(name);
        application.setTeamId(teamId);

        applicationMapper.insert(application);

        return applicationMapper.selectById(application.getId());
    }

    public Application getApplicationById(Long id) {

        Application application = applicationMapper.selectById(id);
        if (application == null) {
            throw new ResourceNotFoundException("APPLICATION_NOT_FOUND", "Application not found");
        }
        return application;
    }

    public List<Application> listApplications() {
        return applicationMapper.selectList(null);
    }

    public Application updateApplication(Long id, String name, Long teamId) {
        Application application = getApplicationById(id);

        ensureTeamExists(teamId);
        ensureApplicationNameAvailable(name, id);

        application.setName(name);
        application.setTeamId(teamId);

        applicationMapper.updateById(application);

        return applicationMapper.selectById(id);
    }

    public void deleteApplication(Long id) {
        getApplicationById(id);
        applicationMapper.deleteById(id);
    }

    private void ensureTeamExists(Long teamId) {
        if (teamMapper.selectById(teamId) == null) {
            throw new ResourceNotFoundException("TEAM_NOT_FOUND", "Team not found");
        }
    }

    private void ensureApplicationNameAvailable(String name, Long excludeId) {
        LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<Application>().eq(Application::getName, name);

        if (excludeId != null) {
            wrapper.ne(Application::getId, excludeId);
        }

        Long count = applicationMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new ConflictException("APPLICATION_NAME_ALREADY_EXISTS", "Application name already exists");
        }
    }
}