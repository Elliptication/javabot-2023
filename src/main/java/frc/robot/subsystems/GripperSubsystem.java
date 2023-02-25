package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.SupplyCurrentLimitConfiguration;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;
import edu.wpi.first.wpilibj.AnalogInput;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.DoubleSolenoid.Value;
import edu.wpi.first.wpilibj.PneumaticsModuleType;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.logging.LoggedReceiver;
import frc.lib.logging.Logger;
import frc.robot.Constants.GlobalConstants;
import frc.robot.Constants.GripperConstants;

public class GripperSubsystem extends SubsystemBase {
    private DoubleSolenoid gripperSolenoid;
    private WPI_TalonSRX gripperMotor = new WPI_TalonSRX(GripperConstants.gripperMotor);

    private GripperState gripperState = GripperState.CLOSED;

    private LoggedReceiver gripperIntakeSpeed;
    private LoggedReceiver gripperEjectSpeed;

    private AnalogInput gamePieceSensor1 = new AnalogInput(0);
    private AnalogInput gamePieceSensor2 = new AnalogInput(1);

    private Timer holdTimer = new Timer();

    public GripperSubsystem() {
        gripperSolenoid = new DoubleSolenoid(
                GlobalConstants.PCM_ID,
                PneumaticsModuleType.REVPH,
                GripperConstants.FORWARD_CHANNEL,
                GripperConstants.REVERSE_CHANNEL);

        gripperMotor.setNeutralMode(NeutralMode.Brake);
        gripperMotor.configVoltageCompSaturation(GlobalConstants.targetVoltage);
        gripperMotor.enableVoltageCompensation(true);
        gripperMotor.setInverted(false);

        SupplyCurrentLimitConfiguration supplyLimit = new SupplyCurrentLimitConfiguration(true, 20, 30, 0.1);

        gripperMotor.configSupplyCurrentLimit(supplyLimit);

        setDefaultCommand(closeGripperCommand());

        gripperIntakeSpeed = Logger.tunable("/Gripper/Intake Speed", 1.0);
        gripperEjectSpeed = Logger.tunable("/Gripper/Eject Speed", -0.3);

        holdTimer.reset();
        holdTimer.start();
    }

    private boolean hasGamePiece() {
        return gamePieceSensor1.getValue() < 50 || gamePieceSensor2.getValue() < 50;
    }

    public Command openGripperCommand() {
        return run(() -> setState(GripperState.OPEN)).until(this::hasGamePiece);
    }

    public Command closeGripperCommand() {
        return runOnce(() -> {
            setState(GripperState.CLOSED);
        });
    }

    public Command ejectFromGripperCommand() {
        return run(() -> setState(GripperState.EJECT));
    }

    public Command dropFromGripperCommand() {
        return run(() -> setState(GripperState.DISABLED));
    }

    public Command gripperCommand() {
        return Commands.either(ejectFromGripperCommand(), openGripperCommand(), this::hasGamePiece);
    }

    @Override
    public void periodic() {
        switch (gripperState) {
            case DISABLED:
                gripperSolenoid.set(Value.kReverse);
                gripperMotor.stopMotor();
                break;
            case OPEN:
                gripperSolenoid.set(Value.kReverse);
                gripperMotor.set(gripperIntakeSpeed.getDouble());
                break;
            case CLOSED:
                gripperSolenoid.set(Value.kForward);

                if (holdTimer.hasElapsed(0.4)) gripperMotor.stopMotor();
                else gripperMotor.set(0.3);

                break;
            case EJECT:
                gripperSolenoid.set(Value.kReverse);
                gripperMotor.set(gripperEjectSpeed.getDouble());
                break;
        }
    }

    private void setState(GripperState gripperState) {
        if (this.gripperState == gripperState) return;

        this.gripperState = gripperState;

        holdTimer.reset();
        holdTimer.start();
    }

    private enum GripperState {
        DISABLED,
        OPEN, // Open with spinning motor
        CLOSED, // Closed and spin motor until supply limit
        EJECT // Reverse motors and then open
    }
}
