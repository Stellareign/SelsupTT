package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


@Data
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class CrptApi {
    public static void main(String[] args) {

        String signature = "L123456789XXD";

        List<ProductDTO> products = new ArrayList<>();

        DocumentDTO documentDTO = new DocumentDTO("Rkkfd89Yun0", "ok", "LP_INTRODUCE_GOODS",
                true, "076-46565696", "076-89561234782",
                "87-4567899", "2020-01-23", "dress", products,
                "2020-01-23", "123456789");

        CrptApi crptApi = new CrptApi(TimeUnit.MILLISECONDS, 10, 100);
        crptApi.createDoc(documentDTO, signature);
    }

    //*****************************************************************************************************************

    private CrptApi.DocService docService = new DocService();
    private CrptApi.FileService fileService = new FileService();
    private DocumentDTO documentDTO = new DocumentDTO();

    //*********************************************************
    private static String sep = File.separator;
    private static final String filePath = "src" + sep + "main" + sep + "resources";
    private String documentFileName;
    //*********************************************************

    private final int requestLimit;
    private final long intervalMillis;

    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public CrptApi(TimeUnit timeUnit, int requestLimit, long intervalMillis) {
        this.intervalMillis = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;
    }

    //**********************************************************************************************
    private static String URL
//            = "https://ismp.crpt.ru/api/v3/lk/documents/create";
            = "https://postman-echo.com/post"; // тест клиента

    HttpClient client = HttpClient.newHttpClient();
    //**********************************************************************************************

    /**
     * Метод для создания документа на основе переданного объекта DocumentDTO.
     * Отправляет его на удалённый сервер с использованием подписи для авторизации.
     *
     * @param documentDTO объект DocumentDTO, содержащий информацию о документе
     * @param signature   строка, представляющая собой подпись для авторизации
     */
    public void createDoc(DocumentDTO documentDTO, String signature) {
        lock.lock();
        try {
            while (requestCounter.get() >= requestLimit) { // пока счётчик больше или равен лимиту
                try {
                    if (!condition.await(intervalMillis, TimeUnit.MILLISECONDS)) {
                        break; // Прерываем цикл, если ожидание не было успешным
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Прерываем поток
                    e.printStackTrace();
                }
            }
            String jsonDoc = docService.createDocumentJson(documentDTO);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonDoc))
                    .header("Authorization", "Basic " +
                            Base64.getEncoder().encodeToString((signature).getBytes()))
                    .build();

            HttpResponse<String> response = null;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            assert response != null;
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                System.out.println("Ваш документ создан. HTTP Status Code: " + response.statusCode());
                System.out.println("Response Body: " + response.body());
            } else {
                System.err.println("При создании документа возникла ошибка. HTTP Status Code: " + response.statusCode() +
                        "\n Повторите попытку или обратитесь в службу поддержки.");
            }
            requestCounter.incrementAndGet(); // увеличиваем счётчик
            condition.signal(); // вызываем signal() после увеличения счётчика запросов
        } finally { // освобождение блокировки после выполнения операций метода
            lock.unlock();
        }
    }

    //******************************************************************************************************************
//*************************************************FileService*********************************************************
    public class FileService {
        /**
         * Метод для сохранения JSON-представления документа в файл.
         *
         * @param jsonDoc JSON-представление документа
         * @return true, если сохранение прошло успешно, иначе false
         */
        public boolean saveDocumentToFile(String jsonDoc, String docId) {
            try {
                Files.writeString(Path.of(filePath, docId + ".json"), jsonDoc);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    //************************************************************************************************************
    //************************************** DocumentService *****************************************************
    public class DocService {
        private final Mapper mapper = new Mapper();
        private final FileService fileService = new FileService();

        //**********************************************************************************************************

        /**
         * Метод для создания JSON-документа на основе объекта DocumentDTO.
         *
         * @param documentDto объект типа DocumentDTO
         * @return JSON документ
         */
        public String createDocumentJson(DocumentDTO documentDto) {
            Description1 description = createDocDescription(documentDto);
            createDocument(documentDto);
            try {
                String json = new ObjectMapper().writeValueAsString(description);
                fileService.saveDocumentToFile(json, documentDto.getDoc_id());
                return json;
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Ошибка при создании json.");
            }
        }

        /**
         * Метод для оздания документа через маппер из DocumentDTO
         *
         * @param documentDTO
         * @return
         */
        public Document createDocument(DocumentDTO documentDTO) {
            return mapper.fromDtoToDocument(documentDTO);
        }

        /**
         * Метод добавления нового продукта в лист документа
         *
         * @param productDTO
         * @param documentDTO
         * @return
         */
        public boolean addProductAtList(ProductDTO productDTO, DocumentDTO documentDTO) {
            List<ProductDTO> productsList = documentDTO.getProducts();
            if (productsList != null) {
                productsList.add(productDTO);
                documentDTO.setProducts(productsList);

            } else {
                List<ProductDTO> productsList2 = new ArrayList<>();
                productsList2.add(productDTO);
                documentDTO.setProducts(productsList2);
            }
            mapper.fromDtoToDocument(documentDTO);
            return true;
        }

    }
    //******************************************************************************************************************
//********************************** Mapper ****************************************************************************
    private class Mapper {
        private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        /**
         * Метод для преобразования объекта DocumentDTO в объект Document
         *
         * @param documentDTO
         * @return new Document()
         */
        private Document fromDtoToDocument(DocumentDTO documentDTO) {
            return new Document(
                    documentDTO.getDoc_id(),
                    documentDTO.getDoc_status(),
                    DocumentsType.valueOf(documentDTO.getDoc_type()),
                    documentDTO.isImportRequest(),
                    documentDTO.getOwner_inn(),
                    documentDTO.getParticipant_inn(),
                    documentDTO.getProducer_inn(),
                    LocalDate.parse(documentDTO.getProduction_date(), formatter),
                    documentDTO.getProduction_type(),
                    createProductListFromDto(documentDTO.getProducts()),
                    LocalDate.parse(documentDTO.getReg_date(), formatter),
                    documentDTO.getReg_number());
        }

        /**
         * Метод для создания списка объектов типа Product на основе списка объектов типа ProductDTO.
         *
         * @param productsDtoList список объектов типа ProductDTO
         * @return список объектов типа Product
         */
        private List<Product> createProductListFromDto(List<ProductDTO> productsDtoList) {
            List<Product> productList = new ArrayList<>();
            for (ProductDTO productDto : productsDtoList) {
                Product product = fromDtoToProduct(productDto);
                productList.add(product);
            }
            return productList;
        }

        /**
         * Метод для преобразования объекта Document в объект DocumentDTO
         *
         * @param document
         * @return new DocumentDTO()
         */
        private DocumentDTO fromDocumentToDto(Document document) {
            return new DocumentDTO(
                    document.getId(),
                    document.getStatus(),
                    document.getProduction_type(),
                    document.isImportRequest(),
                    document.getOwner_inn(),
                    document.getParticipant_inn(),
                    document.getProducer_inn(),
                    document.getProduction_date().format(formatter),
                    document.getProduction_type(),
                    createProductDTOList(document.getProducts()),
                    document.getReg_date().format(formatter),
                    document.getReg_number());
        }

        /**
         * Метод для создания списка объектов типа ProductDTO на основе списка объектов типа Product.
         *
         * @param productsList список объектов типа Product
         * @return список объектов типа ProductDTO
         */
        private List<ProductDTO> createProductDTOList(List<Product> productsList) {

            List<ProductDTO> productDTOList = new ArrayList<>();
            for (Product product : productsList) {
                ProductDTO productDTO = fromProductToDTO(product);
                productDTOList.add(productDTO);
            }
            return productDTOList;
        }

        /**
         * Метод для преобразования объекта Product в объект ProductDTO
         *
         * @param product
         * @return new ProductDTO()
         */
        private ProductDTO fromProductToDTO(Product product) {
            return new ProductDTO(
                    product.getCertificate_document(),
                    product.getCertificate_document_date().format(formatter),
                    product.getCertificate_document_number(),
                    product.getOwner_inn(),
                    product.getProducer_inn(),
                    product.getProduction_date().format(formatter),
                    product.getTnved_code(),
                    product.getUit_code(),
                    product.getUitu_code());
        }

        /**
         * Метод для преобразования объекта ProductDTO в объект Product
         *
         * @param productDTO
         * @return new Product()
         */
        private Product fromDtoToProduct(ProductDTO productDTO) {
            return new Product(
                    productDTO.getCertificate_document(),
                    LocalDate.parse(productDTO.getCertificate_document_date(), formatter),
                    productDTO.getCertificate_document_number(),
                    productDTO.getOwner_inn(),
                    productDTO.getProducer_inn(),
                    LocalDate.parse(productDTO.getProduction_date(), formatter),
                    productDTO.getTnved_code(),
                    productDTO.getUit_code(),
                    productDTO.getUitu_code());
        }
    }

    /**
     * Метод для создания Description из DocumentDTO для дальнейшей отправки в теле запроса в методе CreateDoc()
     *
     * @param documentDTO
     * @return
     */
    private Description1 createDocDescription(DocumentDTO documentDTO) {
        return new Description1(
                new ParticipantInn(documentDTO.getParticipant_inn()),
                documentDTO.getDoc_id(),
                documentDTO.getDoc_status(),
                documentDTO.getDoc_type(),
                documentDTO.isImportRequest(),
                documentDTO.getOwner_inn(),
                documentDTO.getParticipant_inn(),
                documentDTO.getProducer_inn(),
                documentDTO.getProduction_date(),
                documentDTO.getProduction_type(),
                documentDTO.getProducts(),
                documentDTO.getReg_date(),
                documentDTO.getReg_number()
        );
    }
//**********************************************************************************************************************
    //************************************************ DTO *************************************************************
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonPropertyOrder({"description", "doc_id", "doc_status", "doc_type",
            "importRequest", "owner_inn", "participant_inn", "producer_inn", "production_date",
            "production_type", "products", "reg_date", "reg_number"})
    public static class Description1 {
        @JsonProperty("description")
        private ParticipantInn participantInn;

        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<ProductDTO> products;
        private String reg_date;
        private String reg_number;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DocumentDTO {
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<ProductDTO> products;
        private String reg_date;
        private String reg_number;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductDTO {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    //******************************************************************************************************************
    //******************************************** BaseClasses ********************************************************
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ParticipantInn {
        private String participantInn;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Document {
        private String id; // id документа
        private String status; // статус документа
        private DocumentsType doc_type; // тип документа
        private boolean importRequest; // флаг запрос на импорт
        private String owner_inn; // ИНН владельца
        private String participant_inn; // ИНН участника
        private String producer_inn; // ИНН производителя
        private LocalDate production_date; // дата производства
        private String production_type; // тип производства (?)
        private List<Product> products; // инфо о продуктах
        private LocalDate reg_date; // дата регистрации документа
        private String reg_number; // регистрационный номер
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Product {
        private String certificate_document; // документ о сертификации (сертификат) продукта
        private LocalDate certificate_document_date; // дата сертификации (?) продукта
        private String certificate_document_number; // номер сертификата на продукт
        private String owner_inn; // ИНН владельца продукта
        private String producer_inn; //  ИНН производителя продукта
        private LocalDate production_date; // дата производства продукта
        private String tnved_code; // код ТН ВЭД
        private String uit_code; // код УКТ ВЭД
        private String uitu_code; // код УТ ВЭДУ

    }

    public enum DocumentsType {
        LP_INTRODUCE_GOODS;
    }

}