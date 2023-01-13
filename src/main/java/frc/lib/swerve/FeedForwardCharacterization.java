// From Mechanical Advantage, Team 6328

package frc.lib.swerve;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.CommandBase;
import edu.wpi.first.wpilibj2.command.Subsystem;
import frc.lib.math.PolynomialRegression;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FeedForwardCharacterization extends CommandBase {
    private static final double startDelaySecs = 1.0;
    private static final double rampRateVoltsPerSec = 0.1;

    private final boolean forwards;
    private final boolean isDrive;

    private final FeedForwardCharacterizationData dataPrimary;
    private final FeedForwardCharacterizationData dataSecondary;
    private final Consumer<Double> voltageConsumerSimple;
    private final BiConsumer<Double, Double> voltageConsumerDrive;
    private final Supplier<Double> velocitySupplierPrimary;
    private final Supplier<Double> velocitySupplierSecondary;

    private final Timer timer = new Timer();

    /** Creates a new FeedForwardCharacterization for a differential drive. */
    public FeedForwardCharacterization(
            Subsystem drive,
            boolean forwards,
            FeedForwardCharacterizationData leftData,
            FeedForwardCharacterizationData rightData,
            BiConsumer<Double, Double> voltageConsumer,
            Supplier<Double> leftVelocitySupplier,
            Supplier<Double> rightVelocitySupplier) {
        addRequirements(drive);
        this.forwards = forwards;
        this.isDrive = true;
        this.dataPrimary = leftData;
        this.dataSecondary = rightData;
        this.voltageConsumerSimple = null;
        this.voltageConsumerDrive = voltageConsumer;
        this.velocitySupplierPrimary = leftVelocitySupplier;
        this.velocitySupplierSecondary = rightVelocitySupplier;
    }

    /** Creates a new FeedForwardCharacterization for a simple subsystem. */
    public FeedForwardCharacterization(
            Subsystem subsystem,
            boolean forwards,
            FeedForwardCharacterizationData data,
            Consumer<Double> voltageConsumer,
            Supplier<Double> velocitySupplier) {
        addRequirements(subsystem);
        this.forwards = forwards;
        this.isDrive = false;
        this.dataPrimary = data;
        this.dataSecondary = null;
        this.voltageConsumerSimple = voltageConsumer;
        this.voltageConsumerDrive = null;
        this.velocitySupplierPrimary = velocitySupplier;
        this.velocitySupplierSecondary = null;
    }

    // Called when the command is initially scheduled.
    @Override
    public void initialize() {
        timer.reset();
        timer.start();
    }

    // Called every time the scheduler runs while the command is scheduled.
    @Override
    public void execute() {
        if (timer.get() < startDelaySecs) {
            if (isDrive) {
                voltageConsumerDrive.accept(0.0, 0.0);
            } else {
                voltageConsumerSimple.accept(0.0);
            }
        } else {
            double voltage = (timer.get() - startDelaySecs) * rampRateVoltsPerSec * (forwards ? 1 : -1);
            if (isDrive) {
                voltageConsumerDrive.accept(voltage, voltage);
            } else {
                voltageConsumerSimple.accept(voltage);
            }
            dataPrimary.add(velocitySupplierPrimary.get(), voltage);
            if (isDrive) {
                dataSecondary.add(velocitySupplierSecondary.get(), voltage);
            }
        }
    }

    // Called once the command ends or is interrupted.
    @Override
    public void end(boolean interrupted) {
        if (isDrive) {
            voltageConsumerDrive.accept(0.0, 0.0);
        } else {
            voltageConsumerSimple.accept(0.0);
        }
        timer.stop();

        // Prevent accidental crashing if no data was recorded
        if(dataPrimary.velocityData.isEmpty()) return;

        dataPrimary.print();
        if (isDrive) {
            dataSecondary.print();
        }
    }

    // Returns true when the command should end.
    @Override
    public boolean isFinished() {
        return false;
    }

    public static class FeedForwardCharacterizationData {
        private final String name;
        private final List<Double> velocityData = new LinkedList<>();
        private final List<Double> voltageData = new LinkedList<>();

        public FeedForwardCharacterizationData(String name) {
            this.name = name;
        }

        public void add(double velocity, double voltage) {
            if (Math.abs(velocity) > 1E-4) {
                velocityData.add(Math.abs(velocity));
                voltageData.add(Math.abs(voltage));
            }
        }

        public void print() {
            double velocityDataArray[] = velocityData.stream().mapToDouble(Double::doubleValue).toArray();
            double voltageDataArray[] = voltageData.stream().mapToDouble(Double::doubleValue).toArray();
            double accelerationDataArray[] = new double[velocityDataArray.length];
            for (int i = 0; i < velocityDataArray.length - 1; i++) {
                accelerationDataArray[i] = (velocityDataArray[i + 1] - velocityDataArray[i]) / 0.020;
            }
            accelerationDataArray[accelerationDataArray.length - 1] = accelerationDataArray[accelerationDataArray.length - 2];

            PolynomialRegression regression = new PolynomialRegression(
                    velocityDataArray,
                    voltageDataArray,
                    1);
            
            double residualsVoltageVelocityWise[] = new double[velocityDataArray.length];

            for (int i = 0; i < velocityDataArray.length; i++) {
                residualsVoltageVelocityWise[i] = voltageDataArray[i] - regression.predict(velocityDataArray[i]);
            }
        

            PolynomialRegression accelerationRegression = new PolynomialRegression(
                accelerationDataArray, residualsVoltageVelocityWise, 1);  

            System.out.println("FF Characterization Results (" + name + "):");
            System.out.println("\tCount=" + Integer.toString(velocityData.size()) + "");
            System.out.println(String.format("\tR2=%.5f", regression.R2()));
            System.out.println(String.format("\tkS=%.5f", regression.beta(0)));
            System.out.println(String.format("\tkV=%.5f", regression.beta(1)));
            System.out.println(String.format("\tkA=%.5f", accelerationRegression.beta(1)));
        }
    }
}
