package com.example.TextileManagement.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "taka_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TakaEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer takaNo;
    private Double meters;

    @ManyToOne
    @JoinColumn(name = "sale_id")
    @JsonBackReference
    private Sale sale;
}
