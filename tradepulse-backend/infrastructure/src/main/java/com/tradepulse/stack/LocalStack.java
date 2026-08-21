package com.tradepulse.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;
import org.jetbrains.annotations.NotNull;

import software.amazon.awscdk.services.ecs.Protocol;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


public class LocalStack extends Stack {

    private static final Map<String, String> DOT_ENV = loadDotEnv();
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost.localstack.cloud:4510,localhost.localstack.cloud:4511,localhost.localstack.cloud:4512";

    private final Vpc vpc;
    private final Cluster ecsCluster;

    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);

        this.vpc = createVpc();

        DatabaseInstance authServiceDb = createDatabaseInstance("AuthServiceDB", "auth_service_db");
        DatabaseInstance customerServiceDb = createDatabaseInstance("CustomerServiceDB", "customer_service_db");
        DatabaseInstance stockServiceDb = createDatabaseInstance("StockServiceDB", "stock_service_db");
        DatabaseInstance orderServiceDb = createDatabaseInstance("OrderServiceDB", "order_service_db");
        DatabaseInstance paymentServiceDb = createDatabaseInstance("PaymentServiceDB", "payment_service_db");
        DatabaseInstance portfolioServiceDb = createDatabaseInstance("PortfolioServiceDB", "portfolio_service_db");
        DatabaseInstance analyticsServiceDb = createDatabaseInstance("AnalyticsServiceDB", "analytics_service_db");

        CfnHealthCheck authDbHealthCheck = createDbHealthCheck(authServiceDb, "AuthServiceDBHealthCheck");
        CfnHealthCheck customerDbHealthCheck = createDbHealthCheck(customerServiceDb, "CustomerServiceDBHealthCheck");
        CfnHealthCheck stockDbHealthCheck = createDbHealthCheck(stockServiceDb, "StockServiceDBHealthCheck");
        CfnHealthCheck orderDbHealthCheck = createDbHealthCheck(orderServiceDb, "OrderServiceDBHealthCheck");
        CfnHealthCheck paymentDbHealthCheck = createDbHealthCheck(paymentServiceDb, "PaymentServiceDBHealthCheck");
        CfnHealthCheck portfolioDbHealthCheck = createDbHealthCheck(portfolioServiceDb, "PortfolioServiceDBHealthCheck");
        CfnHealthCheck analyticsDbHealthCheck = createDbHealthCheck(analyticsServiceDb, "AnalyticsServiceDBHealthCheck");

        CfnCluster mskCluster = createMskCluster();

        this.ecsCluster = createEcsCluster();

        // ── auth-service ─────────────────────────────────────────────────────────
        FargateService authService = createFargateService("AuthService",
                "auth-service",
                List.of(4005),
                authServiceDb,
                "auth_service_db",
                Map.ofEntries(
                        Map.entry("JWT_SECRET",                    DOT_ENV.get("JWT_SECRET")),
                        Map.entry("AUTH_INTERNAL_API_KEY",         DOT_ENV.get("AUTH_INTERNAL_API_KEY")),
                        Map.entry("MAIL_HOST",                     DOT_ENV.get("MAIL_HOST")),
                        Map.entry("MAIL_PORT",                     DOT_ENV.get("MAIL_PORT")),
                        Map.entry("MAIL_USERNAME",                 DOT_ENV.get("MAIL_USERNAME")),
                        Map.entry("MAIL_PASSWORD",                 DOT_ENV.get("MAIL_PASSWORD")),
                        Map.entry("MAIL_FROM",                     DOT_ENV.get("MAIL_FROM")),
                        Map.entry("MAIL_SMTP_AUTH",                DOT_ENV.get("MAIL_SMTP_AUTH")),
                        Map.entry("MAIL_SMTP_STARTTLS_ENABLE",     DOT_ENV.get("MAIL_SMTP_STARTTLS_ENABLE")),
                        Map.entry("MAIL_SMTP_STARTTLS_REQUIRED",   DOT_ENV.get("MAIL_SMTP_STARTTLS_REQUIRED")),
                        Map.entry("MAIL_SMTP_CONNECTION_TIMEOUT",  DOT_ENV.get("MAIL_SMTP_CONNECTION_TIMEOUT")),
                        Map.entry("MAIL_SMTP_TIMEOUT",             DOT_ENV.get("MAIL_SMTP_TIMEOUT")),
                        Map.entry("MAIL_SMTP_WRITE_TIMEOUT",       DOT_ENV.get("MAIL_SMTP_WRITE_TIMEOUT"))
                ));
        authService.getNode().addDependency(authDbHealthCheck);
        authService.getNode().addDependency(authServiceDb);

        // ── customer-service ─────────────────────────────────────────────────────
        FargateService customerService = createFargateService("CustomerService",
                "customer-service",
                List.of(4000),
                customerServiceDb,
                "customer_service_db",
                Map.ofEntries(
                        Map.entry("SPRING_KAFKA_BOOTSTRAP_SERVERS", KAFKA_BOOTSTRAP_SERVERS),
                        Map.entry("AUTH_INTERNAL_API_KEY",         DOT_ENV.get("AUTH_INTERNAL_API_KEY")),
                        Map.entry("AUTH_SERVICE_BASE_URL",         "http://auth-service:4005")
                ));
        customerService.getNode().addDependency(customerDbHealthCheck);
        customerService.getNode().addDependency(customerServiceDb);
        customerService.getNode().addDependency(authService);

        // ── payment-service ──────────────────────────────────────────────────────
        FargateService paymentService = createFargateService("PaymentService",
                "payment-service",
                List.of(4001, 9002),
                paymentServiceDb,
                "payment_service_db",
                Map.ofEntries(
                        Map.entry("SPRING_KAFKA_BOOTSTRAP_SERVERS", KAFKA_BOOTSTRAP_SERVERS)
                ));
        paymentService.getNode().addDependency(paymentDbHealthCheck);
        paymentService.getNode().addDependency(paymentServiceDb);

        // ── analytics-service ─────────────────────────────────────────────────────
        FargateService analyticsService = createFargateService("AnalyticsService",
                "analytics-service",
                List.of(4010),
                analyticsServiceDb,
                "analytics_service_db",
                Map.ofEntries(
                        Map.entry("ML_DATABASE_URL",                        "postgresql+psycopg2://" + DOT_ENV.get("POSTGRES_USER") + ":" + DOT_ENV.get("POSTGRES_PASSWORD") + "@analytics-service-db:5432/" + DOT_ENV.get("POSTGRES_DB")),
                        Map.entry("ML_MODEL_PATH",                          "/ml-model/tradepulse_model.joblib"),
                        Map.entry("ML_SERVICE_PORT",                        "4010"),
                        Map.entry("STOCK_SERVICE_BASE_URL",                 "http://stock-service:4003"),
                        Map.entry("MASSIVE_API_BASE_URL",                   "https://api.massive.com"),
                        Map.entry("MASSIVE_API_KEY",                        DOT_ENV.get("MASSIVE_API_KEY")),
                        Map.entry("MASSIVE_NEWS_LIMIT",                     "5"),
                        Map.entry("OHLC_YEARS_BACK",                        "3"),
                        Map.entry("ML_DEFAULT_DAYS_BACK",                   "365"),
                        Map.entry("ML_DEFAULT_HORIZON_DAYS",                "5"),
                        Map.entry("ML_MAX_TRAINING_STOCKS",                 "100"),
                        Map.entry("FRESHNESS_CHECK_ENABLED",                "true"),
                        Map.entry("FRESHNESS_STARTUP_CATCHUP_ENABLED",      "true"),
                        Map.entry("FRESHNESS_POLL_INTERVAL_MINUTES",        "5"),
                        Map.entry("FRESHNESS_MORNING_HOUR_ET",              "5"),
                        Map.entry("FRESHNESS_MORNING_MINUTE_ET",            "0"),
                        Map.entry("FRESHNESS_TIMEZONE",                     "America/New_York"),
                        Map.entry("ML_TRAIN_ON_STARTUP",                    "true"),
                        Map.entry("ML_RETRAIN_INTERVAL_HOURS",              "168")
                ));
        analyticsService.getNode().addDependency(analyticsDbHealthCheck);
        analyticsService.getNode().addDependency(analyticsServiceDb);

        // ── stock-service ────────────────────────────────────────────────────────
        FargateService stockService = createFargateService("StockService",
                "stock-service",
                List.of(4003, 9003),
                stockServiceDb,
                "stock_service_db",
                Map.ofEntries(
                        Map.entry("MASSIVE_API_KEY",                    DOT_ENV.get("MASSIVE_API_KEY")),
                        Map.entry("GRPC_SERVER_PORT",                   "9003")
                ));
        stockService.getNode().addDependency(stockDbHealthCheck);
        stockService.getNode().addDependency(stockServiceDb);

        // analytics-service depends on stock-service being healthy (matches compose)
        analyticsService.getNode().addDependency(stockService);

        // ── portfolio-service ────────────────────────────────────────────────────
        FargateService portfolioService = createFargateService("PortfolioService",
                "portfolio-service",
                List.of(4007),
                portfolioServiceDb,
                "portfolio_service_db",
                Map.ofEntries(
                        Map.entry("SPRING_KAFKA_BOOTSTRAP_SERVERS", KAFKA_BOOTSTRAP_SERVERS),
                        Map.entry("AUTH_SERVICE_BASE_URL",            "http://auth-service:4005"),
                        Map.entry("STOCK_SERVICE_BASE_URL",           "http://stock-service:4003"),
                        Map.entry("PAYMENT_SERVICE_GRPC_ADDRESS",     "payment-service"),
                        Map.entry("PAYMENT_SERVICE_GRPC_PORT",        "9002"),
                        Map.entry("CUSTOMER_SERVICE_BASE_URL",        "http://customer-service:4000")
                ));
        portfolioService.getNode().addDependency(portfolioDbHealthCheck);
        portfolioService.getNode().addDependency(portfolioServiceDb);
        portfolioService.getNode().addDependency(stockService);

        // ── order-service ────────────────────────────────────────────────────────
        FargateService orderService = createFargateService("OrderService",
                "order-service",
                List.of(4006),
                orderServiceDb,
                "order_service_db",
                Map.ofEntries(
                        Map.entry("SPRING_KAFKA_BOOTSTRAP_SERVERS", KAFKA_BOOTSTRAP_SERVERS),
                        Map.entry("ORDER_PAYMENT_SERVICE_ADDRESS",      "payment-service"),
                        Map.entry("ORDER_PAYMENT_SERVICE_GRPC_PORT",    "9002"),
                        Map.entry("STOCK_SERVICE_GRPC_ADDRESS",         "stock-service"),
                        Map.entry("STOCK_SERVICE_GRPC_PORT",            "9003")
                ));
        orderService.getNode().addDependency(orderDbHealthCheck);
        orderService.getNode().addDependency(orderServiceDb);
        orderService.getNode().addDependency(paymentService);
        orderService.getNode().addDependency(customerService);
        orderService.getNode().addDependency(stockService);
        orderService.getNode().addDependency(portfolioService);

        // ── api-gateway ──────────────────────────────────────────────────────────
        FargateService apiGatewayService = createFargateService("ApiGateway",
                "api-gateway",
                List.of(4004),
                null,
                null,
                Map.of(
                        "AUTH_SERVICE_URL",       "http://auth-service:4005",
                        "CUSTOMER_SERVICE_URL",   "http://customer-service:4000",
                        "PORTFOLIO_SERVICE_URL",  "http://portfolio-service:4007",
                        "STOCK_SERVICE_URL",      "http://stock-service:4003"
                ));
        apiGatewayService.getNode().addDependency(authService);
        apiGatewayService.getNode().addDependency(customerService);
        apiGatewayService.getNode().addDependency(stockService);
        apiGatewayService.getNode().addDependency(orderService);
        apiGatewayService.getNode().addDependency(portfolioService);
        apiGatewayService.getNode().addDependency(analyticsService);

        // ── notification-service ─────────────────────────────────────────────────
        FargateService notificationService = createFargateService("NotificationService",
                "notification-service",
                List.of(4008),
                null,
                null,
                Map.ofEntries(
                        Map.entry("SPRING_KAFKA_BOOTSTRAP_SERVERS",     KAFKA_BOOTSTRAP_SERVERS),
                        Map.entry("AUTH_SERVICE_BASE_URL",             "http://auth-service:4005"),
                        Map.entry("CUSTOMER_SERVICE_BASE_URL",         "http://customer-service:4000"),
                        Map.entry("MAIL_HOST",                         DOT_ENV.get("MAIL_HOST")),
                        Map.entry("MAIL_PORT",                         DOT_ENV.get("MAIL_PORT")),
                        Map.entry("MAIL_USERNAME",                     DOT_ENV.get("MAIL_USERNAME")),
                        Map.entry("MAIL_PASSWORD",                     DOT_ENV.get("MAIL_PASSWORD")),
                        Map.entry("MAIL_FROM",                         DOT_ENV.get("MAIL_FROM")),
                        Map.entry("MAIL_SMTP_AUTH",                    DOT_ENV.get("MAIL_SMTP_AUTH")),
                        Map.entry("MAIL_SMTP_STARTTLS_ENABLE",         DOT_ENV.get("MAIL_SMTP_STARTTLS_ENABLE")),
                        Map.entry("MAIL_SMTP_STARTTLS_REQUIRED",       DOT_ENV.get("MAIL_SMTP_STARTTLS_REQUIRED")),
                        Map.entry("MAIL_SMTP_CONNECTION_TIMEOUT",      DOT_ENV.get("MAIL_SMTP_CONNECTION_TIMEOUT")),
                        Map.entry("MAIL_SMTP_TIMEOUT",                 DOT_ENV.get("MAIL_SMTP_TIMEOUT")),
                        Map.entry("MAIL_SMTP_WRITE_TIMEOUT",           DOT_ENV.get("MAIL_SMTP_WRITE_TIMEOUT"))
                ));
        notificationService.getNode().addDependency(authService);
        notificationService.getNode().addDependency(customerService);
        notificationService.getNode().addDependency(mskCluster);
    }


    private Vpc createVpc() {
        return Vpc.Builder.create(this, "TradePulseVPC")
                .vpcName("TradePulseVPC")
                .maxAzs(2)
                .build();
    }

    private DatabaseInstance createDatabaseInstance(String id, String dbName) {
        return DatabaseInstance.Builder.create(this, id)
                .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder()
                        .version(PostgresEngineVersion.VER_17_2).build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private CfnHealthCheck createDbHealthCheck(DatabaseInstance db, String id) {
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build())
                .build();
    }

    private CfnCluster createMskCluster() {
        return CfnCluster.Builder.create(this,"MskCluster")
                .clusterName("kafka-cluster")
                .kafkaVersion("2.8.0")
                .numberOfBrokerNodes(1)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.large")
                        .clientSubnets(vpc.getPrivateSubnets().stream().map(ISubnet::getSubnetId).collect(Collectors.toList()))
                        .brokerAzDistribution("DEFAULT").build())
                .build();
    }


    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "TradePulseCluster")
                .vpc(vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("tradepulse.local")
                        .build())
                .build();
    }


    private FargateService createFargateService(String id, String imageName, List<Integer> ports, DatabaseInstance db, String databaseName, Map<String, String> additionalEnvVars) {
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, id + "Task")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        ContainerDefinitionOptions.Builder containerOptions = ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry(imageName))
                .portMappings(ports.stream()
                        .map(port -> PortMapping.builder()
                                .containerPort(port)
                                .hostPort(port)
                                .protocol(Protocol.TCP)
                                .build())
                        .toList())
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                .logGroupName("/ecs/" + imageName)
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_DAY)
                                .build())
                        .streamPrefix(imageName)
                        .build()));

        Map<String, String> envVars = new HashMap<>();

        if(additionalEnvVars != null) {
            envVars.putAll(additionalEnvVars);
        }


        if(db != null) {
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s".formatted(
                    db.getDbInstanceEndpointAddress(),
                    db.getDbInstanceEndpointPort(),
                    databaseName
            ) );
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            var secret = Objects.requireNonNull(
                    db.getSecret(),
                    "Database secret is required to set SPRING_DATASOURCE_PASSWORD"
            );
            var passwordSecret = Objects.requireNonNull(
                    secret.secretValueFromJson("password"),
                    "Database secret is missing password field"
            );
            envVars.put("SPRING_DATASOURCE_PASSWORD", passwordSecret.toString());
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }

        containerOptions.environment(envVars);

        taskDefinition.addContainer(imageName + "Container", containerOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .build();
    }



    /** Loads key=value pairs from tradepulse-backend/.env (one level above infrastructure/). */
    private static Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        // Resolve relative to the infrastructure module working directory
        File envFile = new File("../.env");
        if (!envFile.exists()) {
            // Fallback: try from project root
            envFile = new File("tradepulse-backend/.env");
        }
        if (!envFile.exists()) {
            System.out.println("[LocalStack] WARNING: .env file not found at " + envFile.getAbsolutePath() + " — relying on OS env vars.");
            return map;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 1) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                map.put(key, value);
            }
            System.out.println("[LocalStack] Loaded " + map.size() + " variables from " + envFile.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("[LocalStack] WARNING: Could not read .env file: " + e.getMessage());
        }
        return map;
    }

    public static void main(final String[] args) {
        System.out.println("App synthesizing in progress...");
        App app = new App(AppProps.builder().outdir("./cdk.out").build());
        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();
        new LocalStack(app, "LocalStack", props);
        app.synth();

        // Pre-clean jsii temp dirs so Node.js cleanup doesn't fail with ENOTEMPTY on Windows
        cleanupJsiiTempDirs();

        System.out.println("Stack synthesized successfully! Output in cdk.out/");
    }

    private static void cleanupJsiiTempDirs() {
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            File[] dirs = tempDir.listFiles(f -> f.isDirectory() &&
                    (f.getName().startsWith("jsii-kernel-") || f.getName().startsWith("jsii-java-runtime")));
            if (dirs != null) {
                for (File dir : dirs) {
                    deleteRecursively(dir.toPath());
                }
            }
        } catch (Exception e) {
            // Ignore — best-effort cleanup
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public @NotNull FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) {
                return FileVisitResult.CONTINUE; // Skip locked files
            }
            @Override
            public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
