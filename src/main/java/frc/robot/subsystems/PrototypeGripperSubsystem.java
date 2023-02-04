package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.DoubleSolenoid.Value;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

public class PrototypeGripperSubsystem extends SubsystemBase {
    private WPI_TalonSRX armWheels = new WPI_TalonSRX(32);
    private DoubleSolenoid extender = new DoubleSolenoid(null, 1, 0);
    PossibleStates currentState = PossibleStates.OFF;

    public PrototypeGripperSubsystem()
    {

    }

    

    public void closeGripper(int gripperSpeed)
    {
        extender.set(Value.kForward);
        armWheels.set(gripperSpeed);
    }

    public void openGripper(int gripperSpeed)
    {
        extender.set(Value.kReverse);
        armWheels.set(-gripperSpeed);
    }

    public void stopGripper()
    {
        extender.set(Value.kOff);
        armWheels.stopMotor();
    }

    public enum PossibleStates
    {
        OFF,
        CLOSED,
        OPEN
    }

}
