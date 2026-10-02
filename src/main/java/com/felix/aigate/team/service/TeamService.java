package com.felix.aigate.team.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.felix.aigate.common.exception.ConflictException;
import com.felix.aigate.common.exception.ResourceNotFoundException;
import com.felix.aigate.team.entity.Team;
import com.felix.aigate.team.mapper.TeamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMapper teamMapper;

    public Team createTeam(String name) {
        ensureTeamNameAvailable(name, null);

        Team team = new Team();
        team.setName(name);
        teamMapper.insert(team);
        return teamMapper.selectById(team.getId());
    }

    public Team getTeamById(Long id) {
        Team team = teamMapper.selectById(id);

        if (team == null) {
            throw new ResourceNotFoundException(
                    "TEAM_NOT_FOUND",
                    "Team not found"
            );
        }

        return team;
    }

    public List<Team> listTeams() {
        return teamMapper.selectList(null);
    }

    public Team updateTeam(Long id, String name) {
        Team team = getTeamById(id);

        ensureTeamNameAvailable(name, id);

        team.setName(name);

        teamMapper.updateById(team);
        return teamMapper.selectById(id);
    }

    public void deleteTeam(Long id) {
        getTeamById(id);
        teamMapper.deleteById(id);
    }

    private void ensureTeamNameAvailable(String name, Long excludeId) {
        LambdaQueryWrapper<Team> wrapper = new LambdaQueryWrapper<Team>()
                .eq(Team::getName, name);

        if (excludeId != null) {
            wrapper.ne(Team::getId, excludeId);
        }

        Long count = teamMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new ConflictException(
                    "TEAM_NAME_ALREADY_EXISTS",
                    "Team name already exists"
            );
        }
    }

}
