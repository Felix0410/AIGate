package com.felix.aigate.employee.controller;

import com.felix.aigate.employee.dto.request.CreateEmployeeRequest;
import com.felix.aigate.employee.dto.request.UpdateEmployeeRequest;
import com.felix.aigate.employee.dto.response.EmployeeResponse;
import com.felix.aigate.employee.entity.Employee;
import com.felix.aigate.employee.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    public EmployeeResponse createEmployee(@RequestBody CreateEmployeeRequest request) {

        Employee employee = employeeService.createEmployee(
                request.getName(),
                request.getEmail(),
                request.getTeamId()
        );

        return toResponse(employee);
    }

    @GetMapping("/{id}")
    public EmployeeResponse getEmployeeById(@PathVariable Long id) {
        return toResponse(employeeService.getEmployeeById(id));
    }

    @GetMapping
    public List<EmployeeResponse> listEmployees() {
        return employeeService.listEmployees()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/{id}")
    public EmployeeResponse updateEmployee(
            @PathVariable Long id,
            @RequestBody UpdateEmployeeRequest request) {

        Employee employee = employeeService.updateEmployee(
                id,
                request.getName(),
                request.getEmail(),
                request.getTeamId()
        );

        return toResponse(employee);
    }

    @DeleteMapping("/{id}")
    public void deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
    }

    private EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getName(),
                employee.getEmail(),
                employee.getTeamId(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}