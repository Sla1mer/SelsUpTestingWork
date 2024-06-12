import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final HttpClient httpClient;
    private final Gson gson;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);

        long interval = timeUnit.toMillis(1);
        scheduler.scheduleAtFixedRate(() -> semaphore.release(requestLimit - semaphore.availablePermits()),
                interval,
                interval,
                TimeUnit.MILLISECONDS
        );
    }

    public CompletableFuture<HttpResponse<String>> createDocument(Document document, String signature) {
        return CompletableFuture.runAsync(() -> {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).thenCompose(aVoid -> {
            String jsonBody = gson.toJson(document);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofMinutes(1))
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        });
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public static class Document {
        @SerializedName("description")
        private Description description;
        @SerializedName("doc_id")
        private String docId;
        @SerializedName("doc_status")
        private String docStatus;
        @SerializedName("doc_type")
        private String docType;
        @SerializedName("importRequest")
        private boolean importRequest;
        @SerializedName("owner_inn")
        private String ownerInn;
        @SerializedName("participant_inn")
        private String participantInn;
        @SerializedName("producer_inn")
        private String producerInn;
        @SerializedName("production_date")
        private String productionDate;
        @SerializedName("production_type")
        private String productionType;
        @SerializedName("products")
        private Product[] products;
        @SerializedName("reg_date")
        private String regDate;
        @SerializedName("reg_number")
        private String regNumber;

        public static class Description {
            @SerializedName("participantInn")
            private String participantInn;

        }

        public static class Product {
            @SerializedName("certificate_document")
            private String certificateDocument;
            @SerializedName("certificate_document_date")
            private String certificateDocumentDate;
            @SerializedName("certificate_document_number")
            private String certificateDocumentNumber;
            @SerializedName("owner_inn")
            private String ownerInn;
            @SerializedName("producer_inn")
            private String producerInn;
            @SerializedName("production_date")
            private String productionDate;
            @SerializedName("tnved_code")
            private String tnvedCode;
            @SerializedName("uit_code")
            private String uitCode;
            @SerializedName("uitu_code")
            private String uituCode;

        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

        Document document = new Document();
        // Далее нужно заполнить документ данными

        String signature = "test-signature";

        api.createDocument(document, signature).thenAccept(response -> {
            System.out.println(response.body());
        }).join();

        api.shutdown();
    }
}
