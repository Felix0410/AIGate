package com.felix.aigate.team.service;

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
        Team team = new Team();
        team.setName(name);
        teamMapper.insert(team);
        return teamMapper.selectById(team.getId());
    }

    public Team getTeamById(Long id) {
        return teamMapper.selectById(id);
    }

    public List<Team> listTeams() {
        return teamMapper.selectList(null);
    }

    public Team updateTeam(Long id, String name) {
        Team team = teamMapper.selectById(id);
        team.setName(name);

        teamMapper.updateById(team);
        return teamMapper.selectById(id);
    }

    public void deleteTeam(Long id) {
        teamMapper.deleteById(id);
    }

}
