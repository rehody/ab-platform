package io.github.rehody.abplatform.controller;

import static io.github.rehody.abplatform.support.AssignmentFixtures.stringValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rehody.abplatform.config.AbstractWebMvcTest;
import io.github.rehody.abplatform.service.AssignmentService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class AssignmentControllerWebMvcTest extends AbstractWebMvcTest {

    @Mock
    private AssignmentService assignmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildStandaloneMockMvc(new AssignmentController(assignmentService));
    }

    @Test
    void resolve_shouldReturnResolvedValueWhenRequestIsValid() throws Exception {
        UUID userId = UUID.randomUUID();
        when(assignmentService.resolve(eq(userId), eq("flag-a"))).thenReturn(stringValue("red"));

        mockMvc.perform(post("/api/v1/assignments/resolve")
                        .contentType(APPLICATION_JSON)
                        .content(("""
                                {"userId":"%s","flagKey":"flag-a"}
                                """).formatted(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.value").value("red"))
                .andExpect(jsonPath("$.value.type").value("STRING"));

        verify(assignmentService).resolve(userId, "flag-a");
    }
}
