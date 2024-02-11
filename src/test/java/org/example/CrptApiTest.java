package org.example;


import org.example.CrptApi.DocService;
import org.example.CrptApi.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static org.example.TestConstants.DOCUMENTDTO;
import static org.example.TestConstants.PRODUCT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class CrptApiTest {


    @Nested
    public class DocServiceTest {
        private final CrptApi crptApi = new CrptApi();
        private final DocService docService = crptApi.new DocService();


        @BeforeEach
        void setUp() {
            for (int i = 0; i < 2; i++) {
                docService.addProductAtList(PRODUCT, DOCUMENTDTO);
            }
        }

        @Test
        public void addProductAtListTest() {
            List<CrptApi.ProductDTO> pl = DOCUMENTDTO.getProducts();
            assertEquals(2, pl.size());
        }

        @Test
        public void createDocumentJsonTest() {
            FileService fileService = mock(FileService.class);
            when(fileService.saveDocumentToFile(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
            String result = docService.createDocumentJson(DOCUMENTDTO);
            assertTrue(result.contains("8787865655DDD"));
            assertTrue(result.contains("2020-01-23"));
        }

        @Test
        public void createDocumentTest() {
            CrptApi.Document document = docService.createDocument(DOCUMENTDTO);
            assertEquals(LocalDate.of(2020, 01, 23), document.getProduction_date());
        }
    }
}