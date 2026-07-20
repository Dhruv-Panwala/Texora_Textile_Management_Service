package com.example.TextileManagement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TextileManagementApplicationTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void dashboardReturnsSummaryForAuthenticatedCompany() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andReturn();
		String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
		MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);

		mockMvc.perform(post("/api/auth/login")
					.session(session)
					.header("X-CSRF-TOKEN", csrfToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"family\",\"password\":\"family123\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/dashboard?period=monthly").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.current.totalSales").exists())
				.andExpect(jsonPath("$.current.totalPurchases").exists());
	}

	@Test
	void loginRejectsSqlLikeUsernameAndPersistsAuthenticatedSession() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andReturn();
		String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
		HttpSession session = csrfResult.getRequest().getSession(false);

		mockMvc.perform(post("/api/auth/login")
					.session((MockHttpSession) session)
					.header("X-CSRF-TOKEN", csrfToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"' OR '1'='1\",\"password\":\"family123\"}"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/auth/login")
					.session((MockHttpSession) session)
					.header("X-CSRF-TOKEN", csrfToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"family\",\"password\":\"family123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").doesNotExist())
				.andExpect(jsonPath("$.username").value("family"));

		mockMvc.perform(get("/api/auth/me").session((MockHttpSession) session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("family"));
	}

}
