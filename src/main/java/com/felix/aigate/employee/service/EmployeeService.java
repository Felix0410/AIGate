package com.felix.aigate.employee.service;

import com.felix.aigate.employee.entity.Employee;
import com.felix.aigate.employee.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeMapper employeeMapper;

    public Employee createEmployee(String name, String email, Long teamId) {

        Employee employee = new Employee();
        employee.setName(name);
        employee.setEmail(email);
        employee.setTeamId(teamId);

        employeeMapper.insert(employee);

        return employeeMapper.selectById(employee.getId());
    }

    public Employee getEmployeeById(Long id) {
        return employeeMapper.selectById(id);
    }

    public List<Employee> listEmployees() {
        return employeeMapper.selectList(null);
    }

    public Employee updateEmployee(Long id, String name, String email, Long teamId) {

        Employee employee = employeeMapper.selectById(id);

        employee.setName(name);
        employee.setEmail(email);
        employee.setTeamId(teamId);

        employeeMapper.updateById(employee);

        return employeeMapper.selectById(id);
    }

    public void deleteEmployee(Long id) {
        employeeMapper.deleteById(id);
    }
}