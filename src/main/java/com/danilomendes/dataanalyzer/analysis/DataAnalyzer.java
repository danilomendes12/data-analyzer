package com.danilomendes.dataanalyzer.analysis;

import com.danilomendes.dataanalyzer.domain.Customer;
import com.danilomendes.dataanalyzer.domain.DataRecord;
import com.danilomendes.dataanalyzer.domain.Report;
import com.danilomendes.dataanalyzer.domain.Sale;
import com.danilomendes.dataanalyzer.domain.SaleItem;
import com.danilomendes.dataanalyzer.domain.Seller;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Acumulador de passada única, instanciado por arquivo (não é bean singleton):
 * cada arquivo tem seu próprio analyzer, então o estado é isolado por design e dispensa sincronização.
 */
public class DataAnalyzer {

    private final Set<String> sellerDocuments = new HashSet<>();
    private final Set<String> customerDocuments = new HashSet<>();
    private final Set<String> registeredSellerNames = new HashSet<>();
    private final Map<String, BigDecimal> volumeBySellerName = new HashMap<>();

    // Sentinela null = nenhuma venda vista ainda; evita materializar a lista de vendas.
    private Long mostExpensiveSaleId;
    private BigDecimal mostExpensiveSaleValue;

    public void accept(DataRecord record) {
        if (record instanceof Seller seller) {
            sellerDocuments.add(seller.document());
            registeredSellerNames.add(seller.name());
        } else if (record instanceof Customer customer) {
            customerDocuments.add(customer.document());
        } else if (record instanceof Sale sale) {
            acceptSale(sale);
        }
    }

    private void acceptSale(Sale sale) {
        BigDecimal total = sale.items().stream()
            .map(DataAnalyzer::itemValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Acumula volume por nome mesmo sem 001 correspondente: o 001 pode chegar depois (ordem livre).
        volumeBySellerName.merge(sale.salesmanName(), total, BigDecimal::add);
        updateMostExpensive(sale.id(), total);
    }

    private static BigDecimal itemValue(SaleItem item) {
        return item.price().multiply(BigDecimal.valueOf(item.quantity()));
    }

    private void updateMostExpensive(long id, BigDecimal value) {
        boolean higherValue = mostExpensiveSaleValue == null || value.compareTo(mostExpensiveSaleValue) > 0;
        boolean tieWithSmallerId = mostExpensiveSaleValue != null
            && value.compareTo(mostExpensiveSaleValue) == 0
            && id < mostExpensiveSaleId;
        if (higherValue || tieWithSmallerId) {
            mostExpensiveSaleValue = value;
            mostExpensiveSaleId = id;
        }
    }

    public Report summarize() {
        // Pior vendedor apenas entre os cadastrados (001); volume 0 para quem não vendeu.
        Optional<String> worstSellerName = registeredSellerNames.stream()
            .min(Comparator
                .comparing((String name) -> volumeBySellerName.getOrDefault(name, BigDecimal.ZERO))
                .thenComparing(Comparator.naturalOrder()));
        return new Report(
            customerDocuments.size(),
            sellerDocuments.size(),
            Optional.ofNullable(mostExpensiveSaleId),
            worstSellerName
        );
    }
}
