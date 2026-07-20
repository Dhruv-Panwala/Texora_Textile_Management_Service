package com.example.TextileManagement.service;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.CustomerRepository;
import com.example.TextileManagement.repository.SaleRepository;
import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.Customer;
import com.example.TextileManagement.entities.PaymentUpdate;
import com.example.TextileManagement.entities.Sale;
import com.example.TextileManagement.entities.TakaEntry;

@Service
public class SaleService {
    private static final int MAX_TAKAS_PER_CHALLAN = 48;
    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final CurrentCompanyContext currentCompanyContext;
    private final BusinessNumberSequenceService numberSequenceService;

    public SaleService(SaleRepository saleRepository, CustomerRepository customerRepository,
            CompanyProfileRepository companyProfileRepository, CurrentCompanyContext currentCompanyContext,
            BusinessNumberSequenceService numberSequenceService) {
        this.saleRepository = saleRepository;
        this.customerRepository = customerRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.currentCompanyContext = currentCompanyContext;
        this.numberSequenceService = numberSequenceService;
    }

    @Transactional
    public Sale saveSale(Sale sale) {
        sale.setId(null);
        sale.setCompany(currentCompany());
        prepareSale(sale, true);
        return saleRepository.save(sale);
    }

    public List<Sale> getAllSales() {
        return saleRepository.findAllByCompany_Id(currentCompanyId());
    }

    public Page<Sale> getSalesPage(int page, int size) {
        return saleRepository.findAllByCompany_Id(currentCompanyId(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100),
                        Sort.by("saleDate").descending()));
    }

    public Sale getSale(Long id) {
        return saleRepository.findByIdAndCompany_Id(id, currentCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale not found"));
    }

    public boolean deleteSaleById(Long id) {
        if (saleRepository.findByIdAndCompany_Id(id, currentCompanyId()).isEmpty()) {
            return false;
        }
        saleRepository.deleteById(id);
        return true;
    }

    @Transactional
    public Sale updateSale(Long id, Sale updatedSale) {
        Sale existingSale = getSale(id);
        existingSale.setCustomer(updatedSale.getCustomer());
        existingSale.setBrokerName(updatedSale.getBrokerName());
        existingSale.setQuality(updatedSale.getQuality());
        existingSale.setSaleDate(updatedSale.getSaleDate());
        existingSale.setChallanNo(updatedSale.getChallanNo());
        existingSale.setBillNo(updatedSale.getBillNo());
        existingSale.setRate(updatedSale.getRate());
        existingSale.getTakaEntries().clear();
        List<TakaEntry> updatedTakas = updatedSale.getTakaEntries() == null ? new ArrayList<>() : updatedSale.getTakaEntries();
        for (TakaEntry taka : updatedTakas) {
            taka.setId(null);
            taka.setSale(existingSale);
            existingSale.getTakaEntries().add(taka);
        }
        prepareSale(existingSale, false);
        return saleRepository.save(existingSale);
    }

    @Transactional
    public Sale markPaid(Long id, PaymentUpdate payment) {
        Sale sale = getSale(id);
        sale.setStatus("PAID");
        applyPaymentDetails(sale, payment);
        return saleRepository.save(sale);
    }

    @Transactional
    public Sale ensureBillNo(Long id) {
        Sale sale = getSale(id);
        if (sale.getBillNo() == null) {
            BusinessNumberSequenceService.AllocatedNumbers numbers = numberSequenceService.reserve(
                    currentCompanyId(), sale.getFinancialYear(), sale.getChallanNo(), null,
                    false, true, sale.getChallanCount() == null ? 1 : sale.getChallanCount());
            sale.setBillNo(numbers.billNumber());
            sale = saleRepository.save(sale);
        }
        return sale;
    }

    private void prepareSale(Sale sale, boolean assignChallanNo) {
        if (sale.getCustomer() == null || sale.getCustomer().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer ID is required");
        }
        Customer customer = customerRepository.findByIdAndCompany_Id(sale.getCustomer().getId(), currentCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found with ID: " + sale.getCustomer().getId()));
        sale.setCustomer(customer);

        if (sale.getSaleDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sale date is required");
        }
        if (sale.getStatus() == null || sale.getStatus().isBlank()) {
            sale.setStatus("PENDING");
        }
        if (sale.getBrokerName() == null || sale.getBrokerName().isBlank()) {
            sale.setBrokerName(customer.getBrokerName());
        }
        if (sale.getQuality() == null || sale.getQuality().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quality is required");
        }
        sale.setQuality(sale.getQuality().trim());
        if (sale.getRate() == null || sale.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rate must be greater than zero");
        }
        sale.setRate(sale.getRate().setScale(2, RoundingMode.HALF_UP));

        List<TakaEntry> takaEntries = sale.getTakaEntries();
        if (takaEntries == null || takaEntries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one taka entry is required");
        }
        for (TakaEntry taka : takaEntries) {
            if (taka.getTakaNo() == null || taka.getTakaNo() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taka number must be greater than zero");
            }
            if (taka.getMeters() == null || taka.getMeters() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taka meters must be greater than zero");
            }
            taka.setSale(sale);
        }

        double totalMeters = takaEntries.stream().mapToDouble(TakaEntry::getMeters).sum();
        if (totalMeters <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total meters must be greater than zero");
        }
        sale.setTotalMeters(totalMeters);
        sale.setDueDate(sale.getSaleDate().plusDays(45));
        sale.setAmount(BigDecimal.valueOf(totalMeters)
                .multiply(sale.getRate())
                .setScale(2, RoundingMode.HALF_UP));

        String financialYear = getFinancialYearFromDate(sale.getSaleDate());
        sale.setFinancialYear(financialYear);
        int challanCount = Math.max(1, (int) Math.ceil(takaEntries.size() / (double) MAX_TAKAS_PER_CHALLAN));
        sale.setChallanCount(challanCount);
        if (sale.getChallanNo() != null && sale.getChallanNo() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challan number must be greater than zero");
        }
        if (sale.getBillNo() != null && sale.getBillNo() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bill number must be greater than zero");
        }

        boolean allocateChallan = assignChallanNo || sale.getChallanNo() == null;
        boolean allocateBill = sale.getBillNo() == null;
        BusinessNumberSequenceService.AllocatedNumbers numbers = numberSequenceService.reserve(
                currentCompanyId(), financialYear, sale.getChallanNo(), sale.getBillNo(),
                allocateChallan, allocateBill, challanCount);
        sale.setChallanNo(numbers.challanNumber());
        sale.setBillNo(numbers.billNumber());
        validateChallanRange(sale, financialYear, sale.getId());
        validateBillNo(sale, financialYear, sale.getId());
    }

    private void applyPaymentDetails(Sale sale, PaymentUpdate payment) {
        PaymentUpdate details = validatePaymentDetails(payment, sale.getSaleDate());
        sale.setPaymentDate(details.paymentDate());
        sale.setPaymentMode(details.paymentMode());
        sale.setChequeNo(details.chequeNo());
    }

    private PaymentUpdate validatePaymentDetails(PaymentUpdate payment, LocalDate saleDate) {
        if (payment == null || payment.paymentDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment date is required");
        }
        if (saleDate != null && payment.paymentDate().isBefore(saleDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment date cannot be before the sale date");
        }
        String mode = payment.paymentMode() == null ? "" : payment.paymentMode().trim().toUpperCase();
        if (!List.of("CASH", "CHEQUE", "UPI").contains(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment mode must be Cash, Cheque, or UPI");
        }
        String chequeNo = payment.chequeNo() == null ? "" : payment.chequeNo().trim();
        if ("CHEQUE".equals(mode) && chequeNo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cheque number is required");
        }
        if (!"CHEQUE".equals(mode)) {
            chequeNo = "";
        }
        return new PaymentUpdate(payment.paymentDate(), mode, chequeNo);
    }

    private String getFinancialYearFromDate(LocalDate date) {
        int year = date.getMonthValue() < 4 ? date.getYear() - 1 : date.getYear();
        return year + "-" + (year + 1);
    }

    private void validateChallanRange(Sale sale, String financialYear, Long existingSaleId) {
        int challanNo = sale.getChallanNo();
        int challanCount = sale.getChallanCount() == null ? 1 : sale.getChallanCount();
        int rangeEnd = challanNo + challanCount - 1;
        boolean overlaps = saleRepository.existsOverlappingChallanRange(
                currentCompanyId(),
                financialYear,
                challanNo,
                rangeEnd,
                existingSaleId);
        if (overlaps) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challan number range overlaps with another sale in this financial year");
        }
    }

    private void validateBillNo(Sale sale, String financialYear, Long existingSaleId) {
        boolean duplicate = existingSaleId == null
                ? saleRepository.existsByCompany_IdAndFinancialYearAndBillNo(currentCompanyId(), financialYear, sale.getBillNo())
                : saleRepository.existsByCompany_IdAndFinancialYearAndBillNoAndIdNot(currentCompanyId(), financialYear, sale.getBillNo(), existingSaleId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bill number already exists in this financial year");
        }
    }

    private Long currentCompanyId() {
        Long companyId = currentCompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context is required");
        }
        return companyId;
    }

    private CompanyProfile currentCompany() {
        return companyProfileRepository.findById(currentCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company not found"));
    }
}
