package com.felix.aigate.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.felix.aigate.common.exception.ConflictException;
import com.felix.aigate.common.exception.ResourceNotFoundException;
import com.felix.aigate.employee.entity.Employee;
import com.felix.aigate.employee.mapper.EmployeeMapper;
import com.felix.aigate.team.mapper.TeamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final TeamMapper teamMapper;

    public Employee createEmployee(String name, String email, Long teamId) {

        ensureTeamExists(teamId);
        ensureEmailAvailable(email, null);

        Employee employee = new Employee();
        employee.setName(name);
        employee.setEmail(email);
        employee.setTeamId(teamId);

        employeeMapper.insert(employee);

        return employeeMapper.selectById(employee.getId());
    }

    public Employee getEmployeeById(Long id) {

        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new ResourceNotFoundException(
                    "EMPLOYEE_NOT_FOUND",
                    "Employee not found"
            );
        }

        return employeeMapper.selectById(id);
    }

    public List<Employee> listEmployees() {
        return employeeMapper.selectList(null);
    }

    public Employee updateEmployee(Long id, String name, String email, Long teamId) {

        Employee employee = getEmployeeById(id);

        ensureTeamExists(teamId);
        ensureEmailAvailable(email, id);

        employee.setName(name);
        employee.setEmail(email);
        employee.setTeamId(teamId);

        employeeMapper.updateById(employee);

        return employeeMapper.selectById(id);
    }

    public void deleteEmployee(Long id) {

        getEmployeeById(id);
        employeeMapper.deleteById(id);
    }

    private void ensureTeamExists(Long teamId) {
        if (teamMapper.selectById(teamId) == null) {
            throw new ResourceNotFoundException(
                    "TEAM_NOT_FOUND",
                    "Team not found"
            );
        }
    }

    private void ensureEmailAvailable(String email, Long excludeId) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
                .eq(Employee::getEmail, email);

        if (excludeId != null) {
            wrapper.ne(Employee::getId, excludeId);
        }

        Long count = employeeMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new ConflictException(
                    "EMAIL_ALREADY_EXISTS",
                    "Email already exists"
            );
        }
    }
}