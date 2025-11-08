package tn.esprit.studentmanagement.services;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.studentmanagement.entities.Department;
import tn.esprit.studentmanagement.repositories.DepartmentRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    @Test
    void shouldReturnAllDepartments() {
        Department d1 = new Department();
        d1.setIdDepartment(1L); d1.setName("IT");
        Department d2 = new Department();
        d2.setIdDepartment(2L); d2.setName("HR");

        when(departmentRepository.findAll()).thenReturn(Arrays.asList(d1, d2));

        List<Department> result = departmentService.getAllDepartments();
        assertEquals(2, result.size());
        assertEquals("IT", result.get(0).getName());

        verify(departmentRepository, times(1)).findAll();
    }

    @Test
    void shouldReturnDepartmentById() {
        Department d = new Department();
        d.setIdDepartment(10L); d.setName("Finance");

        when(departmentRepository.findById(10L)).thenReturn(Optional.of(d));

        Department result = departmentService.getDepartmentById(10L);
        assertNotNull(result);
        assertEquals(10L, result.getIdDepartment());
        assertEquals("Finance", result.getName());

        verify(departmentRepository, times(1)).findById(10L);
    }

    @Test
    void shouldThrowWhenDepartmentNotFound() {
        when(departmentRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> departmentService.getDepartmentById(999L));
        verify(departmentRepository, times(1)).findById(999L);
    }

    @Test
    void shouldSaveDepartment() {
        Department toSave = new Department();
        toSave.setName("R&D");

        Department saved = new Department();
        saved.setIdDepartment(5L); saved.setName("R&D");

        when(departmentRepository.save(toSave)).thenReturn(saved);

        Department result = departmentService.saveDepartment(toSave);
        assertNotNull(result.getIdDepartment());
        assertEquals(5L, result.getIdDepartment());
        assertEquals("R&D", result.getName());

        verify(departmentRepository, times(1)).save(toSave);
    }

    @Test
    void shouldDeleteDepartmentById() {
        doNothing().when(departmentRepository).deleteById(3L);

        departmentService.deleteDepartment(3L);

        verify(departmentRepository, times(1)).deleteById(3L);
    }
}
