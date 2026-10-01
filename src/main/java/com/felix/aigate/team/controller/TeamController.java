package com.felix.aigate.team.controller;

import com.felix.aigate.team.dto.request.CreateTeamRequest;
import com.felix.aigate.team.dto.request.UpdateTeamRequest;
import com.felix.aigate.team.dto.response.TeamResponse;
import com.felix.aigate.team.entity.Team;
import com.felix.aigate.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public TeamResponse createTeam(@RequestBody CreateTeamRequest request) {

        Team team = teamService.createTeam(request.getName());
        return toResponse(team);

    }

    @GetMapping("/{id}")
    public TeamResponse getTeamById(@PathVariable Long id) {
        Team team = teamService.getTeamById(id);
        return toResponse(team);
    }

    @GetMapping
    public List<TeamResponse> listTeams() {
        return teamService.listTeams().stream().map(this::toResponse).toList();
    }

    @PutMapping("/{id}")
    public TeamResponse updateTeam(@PathVariable Long id, @RequestBody UpdateTeamRequest request) {
        Team team = teamService.updateTeam(id, request.getName());
        return toResponse(team);
    }

    @DeleteMapping("/{id}")
    public void deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(id);
    }



    private TeamResponse toResponse(Team team){
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }

}
