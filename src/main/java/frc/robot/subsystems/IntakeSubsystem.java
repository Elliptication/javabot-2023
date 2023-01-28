package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.SupplyCurrentLimitConfiguration;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.loops.Updatable;
import frc.robot.Constants.GlobalConstants;

public class IntakeSubsystem extends SubsystemBase{
    private WPI_TalonSRX intakeMotor = new WPI_TalonSRX(14);

    //NT
    private double INTAKE_MOTOR_SPEED = 0.7;
    private double REVERSE_INTAKE_MOTOR_SPEED = -0.5;
    private double SHOOTING_MOTOR_SPEED = -1.0; //change value

    private IntakeMode intakeMode = IntakeMode.DISABLED;

    private NetworkTable intakeTable = NetworkTableInstance.getDefault().getTable("intake");
    private DoublePublisher intakeMotorSpeedPublisher = intakeTable.getDoubleTopic("/intake/intake motor speed").publish();
    private DoublePublisher reverseIntakeMotorSpeedPublisher = intakeTable.getDoubleTopic("/intake/reverse intake motor speed").publish();
    private DoublePublisher shootingMotorSpeedPublisher = intakeTable.getDoubleTopic("/intake/shooting motor speed").publish();
    private DoubleSubscriber intakeMotorSpeedSubscriber = intakeTable.getDoubleTopic("/intake/intake motor speed").subscribe(0);
    private DoubleSubscriber reverseIntakeMotorSpeedSubscriber = intakeTable.getDoubleTopic("/intake/reverse intake motor speed").subscribe(0);
    private DoubleSubscriber shootingMotorSpeedSubscriber = intakeTable.getDoubleTopic("/intake/shooting motor speed").subscribe(0);
    private DoublePublisher intakeMotorCurrentPublisher = intakeTable.getDoubleTopic("/intake/intake motor current").publish();

    public IntakeSubsystem() {
        intakeMotor.setNeutralMode(NeutralMode.Brake);
        intakeMotor.configVoltageCompSaturation(GlobalConstants.targetVoltage);
        intakeMotor.enableVoltageCompensation(true);
        intakeMotor.setInverted(true);

        SupplyCurrentLimitConfiguration supplyLimit = new SupplyCurrentLimitConfiguration(
                true,
                20,
                30,
                0.1);

        intakeMotor.configSupplyCurrentLimit(supplyLimit);

        intakeMotorSpeedPublisher.set(INTAKE_MOTOR_SPEED);
        reverseIntakeMotorSpeedPublisher.set(REVERSE_INTAKE_MOTOR_SPEED);
        shootingMotorSpeedPublisher.set(SHOOTING_MOTOR_SPEED);

        setDefaultCommand(stopIntakeCommand());
    }

    public Command runIntakeCommand() {
        return run(() -> {
            setIntakeMode(IntakeMode.INTAKE);
        });
    }

    public Command reverseIntakeCommand() {
        return run(() -> {
            setIntakeMode(IntakeMode.REVERSE);
        });
    }
    
    public Command shootCommand() {
        return run(() -> {
            setIntakeMode(IntakeMode.SHOOT);
        });
    }

    public Command stopIntakeCommand() {
        return run(() -> {
            setIntakeMode(IntakeMode.DISABLED);
        });
    }

    public void runIntake() {
        intakeMotor.set(ControlMode.PercentOutput, intakeMotorSpeedSubscriber.get());
    }

    public void stopIntake() {
        intakeMotor.stopMotor();
    }

    public void reverseIntake() {
        intakeMotor.set(ControlMode.PercentOutput, reverseIntakeMotorSpeedSubscriber.get());
    }

    public void shoot() {
        intakeMotor.set(ControlMode.PercentOutput, shootingMotorSpeedSubscriber.get());
    }

    public void setIntakeMode(IntakeMode intakeMode) {
        this.intakeMode = intakeMode;
    }

    public IntakeMode getIntakeMode() {
        return intakeMode;
    }

    @Override
    public void periodic() {
        intakeMotorCurrentPublisher.set(intakeMotor.getSupplyCurrent());

        switch (intakeMode) {
            case DISABLED:
                stopIntake();
                break;
            case INTAKE:
                runIntake();
                break;
            case REVERSE:
                reverseIntake();
                break;    
            case SHOOT:
                shoot();
                break;
        }
    }

    public enum IntakeMode {
        DISABLED,
        INTAKE,
        REVERSE,
        SHOOT
    }
}
