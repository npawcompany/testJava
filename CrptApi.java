
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("requestLimit must be positive");
        }
        
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.semaphore = new Semaphore(requestLimit);
        
        // Scheduler to reset the semaphore after the specified time interval
        this.scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> semaphore.release(requestLimit), timeUnit.toMillis(1), timeUnit.toMillis(1), TimeUnit.MILLISECONDS);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        // Acquire a permit before making the request
        semaphore.acquire();
        
        try {
            String jsonRequest = objectMapper.writeValueAsString(document);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + signature) // Assuming the signature is a Bearer token
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Failed to create document: " + response.body());
            }
        } finally {
            // Release the permit after the request is completed
            semaphore.release();
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    // Document class should be defined according to the API specifications
    public static class Document {
        // Define fields according to the API requirements
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type = "LP_INTRODUCE_GOODS"; // Fixed value
        private boolean importRequest = true; // Fixed value
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private Product[] products;
        private String reg_date;
        private String reg_number;

        // Getters and Setters
        // ...
        
        public static class Description {
            private String participantInn;

            // Getters and Setters
            // ...
        }

        public static class Product {
            private String certificate_document;
            private String certificate_document_date;
            private String certificate_document_number;
            private String owner_inn;
            private String producer_inn;
            private String production_date;
            private String tnved_code;
            private String uit_code;
            private String uitu_code;

            // Getters and Setters
            // ...
        }
    }
}

public class Main {
    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);
        
        try {
            CrptApi.Document document = new CrptApi.Document();
            // Заполнение полей документа...
            
            api.createDocument(document, "your_signature_here");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            api.shutdown();
        }
    }
}
