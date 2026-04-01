package com.procurehub.product;

import com.procurehub.grpc.product.v1.GetProductByIdRequest;
import com.procurehub.grpc.product.v1.GetProductByIdResponse;
import com.procurehub.product.api.ErrorResponse;
import com.procurehub.product.api.GlobalExceptionHandler;
import com.procurehub.product.api.HealthController;
import com.procurehub.product.api.ProductController;
import com.procurehub.product.config.SecurityConfig;
import com.procurehub.product.dto.CreateProductRequest;
import com.procurehub.product.dto.ProductResponse;
import com.procurehub.product.dto.UpdateProductRequest;
import com.procurehub.product.exception.NotFoundException;
import com.procurehub.product.grpc.ProductGrpcService;
import com.procurehub.product.model.ProductDocument;
import com.procurehub.product.repository.ProductRepository;
import com.procurehub.product.service.ProductService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductModuleSupportCoverageTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void dtoModelHealthAndErrorTypesShouldWork() {
        CreateProductRequest create = new CreateProductRequest();
        create.setName("Keyboard");
        create.setDescription("Mechanical");
        create.setCategory("peripherals");
        create.setPrice(BigDecimal.valueOf(99.90));
        assertEquals("Keyboard", create.getName());

        UpdateProductRequest update = new UpdateProductRequest();
        update.setName("Mouse");
        update.setDescription("Wireless");
        update.setCategory("peripherals");
        update.setPrice(BigDecimal.valueOf(59.90));
        assertEquals("Mouse", update.getName());

        ProductResponse response = new ProductResponse();
        LocalDateTime now = LocalDateTime.now();
        response.setId("p-1");
        response.setName("Desk");
        response.setDescription("Standing");
        response.setCategory("office");
        response.setPrice(BigDecimal.TEN);
        response.setPublished(true);
        response.setCreatedAt(now);
        response.setUpdatedAt(now);
        assertEquals("p-1", response.getId());
        assertEquals(true, response.isPublished());

        ProductDocument document = new ProductDocument();
        document.setId("p-2");
        document.setName("Lamp");
        document.setDescription("LED");
        document.setCategory("office");
        document.setPrice(BigDecimal.ONE);
        document.setPublished(false);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        assertEquals("Lamp", document.getName());
        assertEquals(false, document.isPublished());

        ErrorResponse error = new ErrorResponse(now, 404, "missing");
        assertEquals(404, error.getStatus());
        assertEquals("missing", error.getMessage());

        assertEquals(
                "UP",
                new HealthController().health().get("status")
        );
    }

    @Test
    void serviceShouldHandleCrudAndSearchFlows() {
        ProductService service = new ProductService(repository, mongoTemplate);

        CreateProductRequest create = new CreateProductRequest();
        create.setName("Keyboard");
        create.setDescription("Mechanical");
        create.setCategory("peripherals");
        create.setPrice(BigDecimal.valueOf(99.90));

        when(repository.save(any(ProductDocument.class))).thenAnswer(invocation -> {
            ProductDocument saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "p-1");
            return saved;
        });

        ProductResponse created = service.create(create);
        assertEquals("p-1", created.getId());
        assertEquals("Keyboard", created.getName());

        ProductDocument existing = new ProductDocument();
        ReflectionTestUtils.setField(existing, "id", "p-1");
        existing.setName("Keyboard");
        existing.setDescription("Mechanical");
        existing.setCategory("peripherals");
        existing.setPrice(BigDecimal.valueOf(99.90));

        when(repository.findById("p-1")).thenReturn(Optional.of(existing));

        UpdateProductRequest update = new UpdateProductRequest();
        update.setName("Keyboard Pro");
        update.setDescription("Hot swap");
        update.setCategory("peripherals");
        update.setPrice(BigDecimal.valueOf(129.90));

        ProductResponse updated = service.update("p-1", update);
        assertEquals("Keyboard Pro", updated.getName());

        ProductResponse byId = service.getById("p-1");
        assertEquals("p-1", byId.getId());

        ProductResponse published = service.setPublished("p-1", true);
        assertEquals(true, published.isPublished());

        when(mongoTemplate.find(any(Query.class), eq(ProductDocument.class))).thenReturn(List.of(existing));
        List<ProductResponse> search = service.search(
                "keyboard",
                "peripherals",
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(150),
                true
        );
        assertEquals(1, search.size());

        service.delete("p-1");
        verify(repository).delete(existing);

        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getById("missing"));
    }

    @Test
    void controllerShouldDelegateToService() {
        ProductService service = mock(ProductService.class);
        ProductController controller = new ProductController(service);
        CreateProductRequest create = new CreateProductRequest();
        UpdateProductRequest update = new UpdateProductRequest();
        ProductResponse response = new ProductResponse();

        when(service.create(create)).thenReturn(response);
        when(service.update("p-1", update)).thenReturn(response);
        when(service.setPublished("p-1", true)).thenReturn(response);
        when(service.getById("p-1")).thenReturn(response);
        when(service.search(null, null, null, null, null)).thenReturn(List.of(response));

        assertEquals(response, controller.create(create));
        assertEquals(response, controller.update("p-1", update));
        controller.delete("p-1");
        assertEquals(response, controller.setPublished("p-1", true));
        assertEquals(response, controller.getById("p-1"));
        assertEquals(1, controller.search(null, null, null, null, null).size());

        verify(service).delete("p-1");
    }

    @Test
    void grpcServiceShouldMapProductAndNotFoundScenarios() {
        ProductService service = mock(ProductService.class);
        ProductGrpcService grpcService = new ProductGrpcService(service);
        @SuppressWarnings("unchecked")
        StreamObserver<GetProductByIdResponse> responseObserver = mock(StreamObserver.class);
        ProductResponse product = new ProductResponse();
        LocalDateTime now = LocalDateTime.now();
        product.setId("p-1");
        product.setName("Keyboard");
        product.setDescription("Mechanical");
        product.setCategory("peripherals");
        product.setPrice(BigDecimal.valueOf(99.90));
        product.setPublished(true);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        when(service.getById("p-1")).thenReturn(product);

        grpcService.getProductById(GetProductByIdRequest.newBuilder().setProductId("p-1").build(), responseObserver);

        ArgumentCaptor<GetProductByIdResponse> responseCaptor = ArgumentCaptor.forClass(GetProductByIdResponse.class);
        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();
        assertEquals("p-1", responseCaptor.getValue().getId());
        assertEquals("99.9", responseCaptor.getValue().getPrice());

        when(service.getById("missing")).thenThrow(new NotFoundException("missing"));
        grpcService.getProductById(GetProductByIdRequest.newBuilder().setProductId("missing").build(), responseObserver);
        verify(responseObserver).onError(any());
    }

    @Test
    void exceptionHandlerShouldBuildExpectedResponses() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> notFound = handler.handleNotFound(new NotFoundException("missing"));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("missing", notFound.getBody().getMessage());

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));

        Method method = ProductModuleSupportCoverageTest.class.getDeclaredMethod("productValidationMethod", CreateProductRequest.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
        ResponseEntity<ErrorResponse> validation = handler.handleValidation(ex);
        assertEquals(HttpStatus.BAD_REQUEST, validation.getStatusCode());
        assertEquals("name: must not be blank", validation.getBody().getMessage());

        ResponseEntity<ErrorResponse> other = handler.handleAny(new RuntimeException("boom"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, other.getStatusCode());
        assertEquals("Internal server error", other.getBody().getMessage());
    }

    @Test
    void securityBeansShouldExposeJwtAuthorities() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(
                config,
                "jwtSecret",
                Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8))
        );

        assertNotNull(config.jwtDecoder());

        JwtAuthenticationConverter converter = config.jwtAuthenticationConverter();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("admin@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("roles", List.of("ROLE_ADMIN"))
                .build();

        assertEquals(
                "ROLE_ADMIN",
                converter.convert(jwt).getAuthorities().iterator().next().getAuthority()
        );
    }

    private void productValidationMethod(CreateProductRequest request) {
    }
}
