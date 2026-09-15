package com.example.TextileManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.controller.PurchaseController;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.Customer;
import com.example.TextileManagement.entities.Purchase;
import com.example.TextileManagement.entities.Sale;
import com.example.TextileManagement.entities.Supplier;
import com.example.TextileManagement.entities.TakaEntry;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.CustomerRepository;
import com.example.TextileManagement.repository.SupplierRepository;
import com.example.TextileManagement.service.SaleService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
		"app.seed.enabled=true",
		"app.auth.family-username=test-family",
		"app.auth.family-password=test-family-password",
		"app.auth.family-email=test-family@example.com"
})
class TextileManagementApplicationTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SaleService saleService;

	@Autowired
	private PurchaseController purchaseController;

	@Autowired
	private CompanyProfileRepository companyRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private CurrentCompanyContext companyContext;

	@Test
	void contextLoads() {
	}

	@Test
	void dashboardReturnsSummaryForAuthenticatedCompany() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andReturn();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
		String csrfToken = csrfCookie.getValue();

		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
					.cookie(csrfCookie)
					.header("X-CSRF-TOKEN", csrfToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"test-family\",\"password\":\"test-family-password\"}"))
				.andExpect(status().isOk())
				.andReturn();
		Cookie sessionCookie = loginResult.getResponse().getCookie("JSESSIONID");

		mockMvc.perform(get("/api/dashboard?period=monthly").cookie(sessionCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.current.totalSales").exists())
				.andExpect(jsonPath("$.current.totalPurchases").exists());
	}

	@Test
	void loginRejectsSqlLikeUsernameAndPersistsAuthenticatedSession() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andReturn();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
		String csrfToken = csrfCookie.getValue();

		mockMvc.perform(post("/api/auth/login")
					.cookie(csrfCookie)
					.header("X-CSRF-TOKEN", csrfToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"' OR '1'='1\",\"password\":\"test-family-password\"}"))
				.andExpect(status().isUnauthorized());

		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
					.cookie(csrfCookie)
					.header("X-CSRF-TOKEN", csrfToken)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"test-family\",\"password\":\"test-family-password\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").doesNotExist())
				.andExpect(jsonPath("$.username").value("test-family"))
				.andReturn();
		Cookie sessionCookie = loginResult.getResponse().getCookie("JSESSIONID");

		mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("test-family"));

		mockMvc.perform(get("/api/auth/bootstrap")
				.cookie(sessionCookie)
				.header("X-Request-ID", "11111111-1111-4111-8111-111111111111"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.username").value("test-family"))
				.andExpect(jsonPath("$.companies").isArray())
				.andExpect(jsonPath("$.companies[0].tradeName").value("Devashish Textile"))
				.andExpect(jsonPath("$.companies[0].version").exists())
				.andExpect(header().string("X-Request-ID", "11111111-1111-4111-8111-111111111111"))
				.andExpect(header().string("Server-Timing", org.hamcrest.Matchers.containsString("db;dur=")));
	}

	@Test
	void updatesWorkWithIdOnlyCustomerAndSupplierPayloads() {
		CompanyProfile company = companyRepository.findFirstByOrderByIdAsc().orElseThrow();
		companyContext.setCompanyId(company.getId());
		try {
			String suffix = Long.toString(System.nanoTime());
			Customer customer = new Customer();
			customer.setName("Customer " + suffix);
			customer.setContact("1234567890");
			customer.setAddress("Test address");
			customer.setGstNo("GST" + suffix);
			customer.setCompany(company);
			customer = customerRepository.save(customer);

			Sale sale = sale(customer, LocalDate.of(2026, 7, 20), 10, 100);
			Sale savedSale = saleService.saveSale(sale);
			Sale saleUpdate = sale(customerReference(customer.getId()), LocalDate.of(2026, 7, 21), 12, 110);
			saleUpdate.setChallanNo(savedSale.getChallanNo());
			saleUpdate.setBillNo(savedSale.getBillNo());
			saleUpdate.setVersion(savedSale.getVersion());
			Sale updatedSale = saleService.updateSale(savedSale.getId(), saleUpdate);
			assertEquals(customer.getId(), updatedSale.getCustomer().getId());

			Supplier supplier = new Supplier();
			supplier.setName("Supplier " + suffix);
			supplier.setContact("1234567890");
			supplier.setAddress("Test address");
			supplier.setCompany(company);
			supplier = supplierRepository.save(supplier);

			Purchase purchase = purchase(supplier, LocalDate.of(2026, 7, 20), 2, 500);
			Purchase savedPurchase = purchaseController.create(purchase).getBody();
			Purchase purchaseUpdate = purchase(supplierReference(supplier.getId()), LocalDate.of(2026, 7, 21), 3, 600);
			purchaseUpdate.setVersion(savedPurchase.getVersion());
			Purchase updatedPurchase = purchaseController.update(savedPurchase.getId(), purchaseUpdate).getBody();
			assertEquals(supplier.getId(), updatedPurchase.getSupplier().getId());
		} finally {
			companyContext.clear();
		}
	}

	private Sale sale(Customer customer, LocalDate date, int meters, int rate) {
		Sale sale = new Sale();
		sale.setCustomer(customer);
		sale.setSaleDate(date);
		sale.setQuality("ARTSILK");
		sale.setRate(BigDecimal.valueOf(rate));
		TakaEntry taka = new TakaEntry();
		taka.setTakaNo(1);
		taka.setMeters(BigDecimal.valueOf(meters));
		sale.setTakaEntries(new ArrayList<>(java.util.List.of(taka)));
		return sale;
	}

	private Customer customerReference(Long id) {
		Customer customer = new Customer();
		customer.setId(id);
		return customer;
	}

	private Purchase purchase(Supplier supplier, LocalDate date, int quantity, int rate) {
		Purchase purchase = new Purchase();
		purchase.setSupplier(supplier);
		purchase.setPurchaseDate(date);
		purchase.setMaterialType("BEAM");
		purchase.setQuantity(BigDecimal.valueOf(quantity));
		purchase.setRate(BigDecimal.valueOf(rate));
		return purchase;
	}

	private Supplier supplierReference(Long id) {
		Supplier supplier = new Supplier();
		supplier.setId(id);
		return supplier;
	}

}
