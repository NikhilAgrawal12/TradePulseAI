package com.tradepulse.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.rds.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class LocalStack extends Stack {

    private final Vpc vpc;
    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);

        this.vpc = createVpc();

        DatabaseInstance authServiceDb = createDatabaseInstance("AuthServiceDB", "auth_service_db");
        DatabaseInstance customerServiceDb = createDatabaseInstance("CustomerServiceDB", "customer_service_db");
        DatabaseInstance stockServiceDb = createDatabaseInstance("StockServiceDB", "stock_service_db");
        DatabaseInstance orderServiceDb = createDatabaseInstance("OrderServiceDB", "order_service_db");
        DatabaseInstance paymentServiceDb = createDatabaseInstance("PaymentServiceDB", "payment_service_db");
        DatabaseInstance portfolioServiceDb = createDatabaseInstance("PortfolioServiceDB", "portfolio_service_db");
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
