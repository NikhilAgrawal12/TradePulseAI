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

import software.amazon.awscdk.services.ecs.Protocol;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class LocalStack extends Stack {

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

        CfnHealthCheck authDbHealthCheck = createDbHealthCheck(authServiceDb, "AuthServiceDBHealthCheck");
        CfnHealthCheck customerDbHealthCheck = createDbHealthCheck(customerServiceDb, "CustomerServiceDBHealthCheck");
        CfnHealthCheck stockDbHealthCheck = createDbHealthCheck(stockServiceDb, "StockServiceDBHealthCheck");
        CfnHealthCheck orderDbHealthCheck = createDbHealthCheck(orderServiceDb, "OrderServiceDBHealthCheck");
        CfnHealthCheck paymentDbHealthCheck = createDbHealthCheck(paymentServiceDb, "PaymentServiceDBHealthCheck");
        CfnHealthCheck portfolioDbHealthCheck = createDbHealthCheck(portfolioServiceDb, "PortfolioServiceDBHealthCheck");

        CfnCluster mskCluster = createMskCluster();

        this.ecsCluster = createEcsCluster();


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

    private FargateService createFargateService(String id, String imageName, List<Integer> ports, DatabaseInstance db, Map<String, String> additionalEnvVars) {
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
                        .build()));

        Map<String, String> envVars = new HashMap<>();
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511,localhost.localstack.cloud:4512");

        if(additionalEnvVars != null) {
            envVars.putAll(additionalEnvVars);
        }

        if(db != null) {
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db".formatted(
                    db.getDbInstanceEndpointAddress(),
                    db.getDbInstanceEndpointPort(),
                    imageName
            ) );
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            envVars.put("SPRING_DATASOURCE_PASSWORD", db.getSecret().secretValueFromJson("password").toString());
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }

        containerOptions.environment(envVars);

        taskDefinition.addContainer(imageName + "Container", containerOptions.build());
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
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE; // Skip locked files
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
