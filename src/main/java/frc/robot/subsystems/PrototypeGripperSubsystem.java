package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.SupplyCurrentLimitConfiguration;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.PneumaticsModuleType;
import edu.wpi.first.wpilibj.DoubleSolenoid.Value;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.logging.LoggedReceiver;
import frc.lib.logging.Logger;
import frc.robot.Constants.GlobalConstants;
import frc.robot.Constants.GripperConstants;

public class PrototypeGripperSubsystem extends SubsystemBase {
  
    private WPI_TalonSRX armWheels = new WPI_TalonSRX(GripperConstants.GRIPPER_MOTOR);
    private DoubleSolenoid extender = new DoubleSolenoid(PneumaticsModuleType.CTREPCM, GripperConstants.FORWARD_CHANNEL, GripperConstants.REVERSE_CHANNEL);
    private PossibleStates currentState = PossibleStates.OFF;

    private LoggedReceiver gripperIntakeSpeed = Logger.receive("/Gripper/Intake Speed", 0.2);
    private LoggedReceiver gripperEjectSpeed = Logger.receive("/Gripper/Intake Speed", -0.2);

    public ProtoypeGripperSubsystem() {
        setDefaultCommand(stopGripperCommand);

        armWheels.setNeutralMode(NeutralMode.Brake);
        armWheels.configVoltageCompSaturation(GlobalConstants.targetVoltage);
        armWheels.enableVoltageCompensation(true);
        armWheels.setInverted(true);

        SupplyCurrentLimitConfiguration supplyLimit = new SupplyCurrentLimitConfiguration(
                true,
                20,
                30,
                0.1);

        armWheels.configSupplyCurrentLimit(supplyLimit);
    }

    @Override
    public void periodic()
    {
       switch (currentState){
            case OFF:
                stopGripper();
                break;
            case DROP:
                dropObject();
                break;
            case CONE_GRAB:
                grabCone();
                break;
            case CUBE_GRAB:
                grabCube();
                break;
            case EJECT:
                ejectFromGripper();
                break;   
            }  
            Logger.log("/Gripper/Current", armWheels.getSupplyCurrent());     
    }

    public Command stopGripperCommand()
    {
        return runOnce(() ->  currentState = PossibleStates.OFF);
    }

    public Command dropObjectFromGripperCommand()
    {
        return runOnce(() ->  currentState = PossibleStates.DROP);
    }

    public Command grabConeWithGripperCommand()
    {
        return runOnce(() ->  currentState = PossibleStates.CONE_GRAB);
    }

    public Command grabCubeWithGripperCommand()
    {
        return runOnce(() ->  currentState = PossibleStates.CUBE_GRAB);
    }

    public Command ejectFromGripperCommand()
    {
        return runOnce(() ->  currentState = PossibleStates.EJECT);
    }

    public void stopGripper()
    {
        extender.set(Value.kReverse);
        armWheels.stopMotor();
    }

    public void dropObject()
    {
        extender.set(Value.kReverse);
    }

    public void grabCone()
    {
        extender.set(Value.kForward);
        armWheels.set(32);
    }

    public void grabCube()
    {
        extender.set(Value.kReverse);
        armWheels.set(32);
    }

    public void ejectFromGripper()
    {
        extender.set(Value.kReverse);
        armWheels.set(-32);
    }

    public enum PossibleStates
    {
        OFF, //wheels off, stays in open state.
        DROP, //just open solenoid.
        CONE_GRAB, //gripper enters close state, wheels run forward.
        CUBE_GRAB, // gripper enters open state, wheels run forward.
        EJECT //gripper opens, wheels run reverse.
    }

    public static void killSwitch()
    {
        //Instantly kills the program.
        //funny method
    }

}
