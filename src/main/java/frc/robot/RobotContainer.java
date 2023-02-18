package frc.robot;

import static edu.wpi.first.wpilibj2.command.Commands.*;

import com.ctre.phoenix.music.Orchestra;

import edu.wpi.first.math.filter.SlewRateLimiter;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj2.command.Command;
import frc.lib.controller.LogitechController;
import frc.lib.controller.ThrustmasterJoystick;
import frc.lib.logging.Logger;
import frc.robot.Constants.ControllerConstants;
import frc.robot.Constants.FieldConstants;
import frc.robot.Constants.FieldConstants.PlacementLocation;
import frc.robot.commands.AimAtPoseCommand;
import frc.robot.commands.DriveToPositionCommand;
import frc.robot.subsystems.*;
import frc.robot.subsystems.ArmSubsystem.ArmState;
import java.util.function.Supplier;

public class RobotContainer {
    private final ThrustmasterJoystick leftDriveController =
            new ThrustmasterJoystick(ControllerConstants.LEFT_DRIVE_CONTROLLER);
    private final ThrustmasterJoystick rightDriveController =
            new ThrustmasterJoystick(ControllerConstants.RIGHT_DRIVE_CONTROLLER);
    private final LogitechController operatorController =
            new LogitechController(ControllerConstants.OPERATOR_CONTROLLER);

    public static Orchestra orchestra = new Orchestra();

    public static SlewRateLimiter forwardRateLimiter = new SlewRateLimiter(16);
    public static SlewRateLimiter strafeRateLimiter = new SlewRateLimiter(16);

    private final SwerveDriveSubsystem swerveDriveSubsystem = new SwerveDriveSubsystem();
    private final LightsSubsystem lightsSubsystem = new LightsSubsystem();
    private final GripperSubsystem gripperSubsystem = new GripperSubsystem();
    private final IntakeSubsystem intakeSubsystem = new IntakeSubsystem();
    // private final VisionSubsystem visionSubsystem =
    //         new VisionSubsystem(swerveDriveSubsystem::addVisionPoseEstimate, swerveDriveSubsystem::getPose);
    private final ArmSubsystem armSubsystem = new ArmSubsystem(swerveDriveSubsystem::getPose);

    public AutonomousManager autonomousManager;

    public RobotContainer(TimedRobot robot) {
        autonomousManager = new AutonomousManager(this);

        configureBindings();
    }

    private void configureBindings() {
        /* Set default commands */
        swerveDriveSubsystem.setDefaultCommand(swerveDriveSubsystem.driveCommand(
                this::getDriveForwardAxis, this::getDriveStrafeAxis, this::getDriveRotationAxis, true));

        /* Set non-button, multi-subsystem triggers */

        /* Set left joystick bindings */
        leftDriveController.getLeftTopLeft().onTrue(runOnce(swerveDriveSubsystem::zeroRotation, swerveDriveSubsystem));
        leftDriveController
                .getLeftTopRight()
                .onTrue(runOnce(() -> swerveDriveSubsystem.setPose(new Pose2d()), swerveDriveSubsystem));
        leftDriveController.getLeftTopMiddle().onTrue(runOnce(swerveDriveSubsystem::switchToBackupGyro));
        leftDriveController.nameLeftTopLeft("Reset Gyro Angle");
        leftDriveController.nameLeftTopRight("Reset Pose");
        leftDriveController.nameLeftTopMiddle("Use NavX");

        leftDriveController.getTrigger().whileTrue(gripperSubsystem.openGripperCommand());
        rightDriveController.getTrigger().whileTrue(gripperSubsystem.ejectFromGripperCommand());
        leftDriveController.nameTrigger("Open Gripper");
        rightDriveController.nameTrigger("Eject From Gripper");

        // leftDriveController
        //         .getRightTopRight()
        //         .toggleOnTrue(armSubsystem.passthroughCommand(
        //                 operatorController.getLeftXAxis(),
        //                 operatorController.getLeftYAxis(),
        //                 operatorController.getRightXAxis()));

        // Leveling
        leftDriveController.getLeftBottomLeft().toggleOnTrue(swerveDriveSubsystem.levelChargeStationCommand());
        leftDriveController.getLeftBottomMiddle().whileTrue(run(swerveDriveSubsystem::lock, swerveDriveSubsystem));
        leftDriveController.nameLeftBottomLeft("Level Charge Station");
        leftDriveController.nameLeftBottomMiddle("Lock Wheels");

        /* Set right joystick bindings */
        rightDriveController.getRightBottomMiddle().whileTrue(swerveDriveSubsystem.characterizeCommand(true, true));
        rightDriveController.getRightBottomRight().whileTrue(swerveDriveSubsystem.characterizeCommand(true, false));
        rightDriveController.nameRightBottomMiddle("Characterize Forwards");
        rightDriveController.nameRightBottomMiddle("Characterize Backwards");

        rightDriveController.getLeftThumb().whileTrue(intakeSubsystem.runIntakeCommand());
        rightDriveController.getRightThumb().whileTrue(intakeSubsystem.reverseIntakeCommand());
        rightDriveController.getBottomThumb().whileTrue(intakeSubsystem.shootCommand());
        rightDriveController.nameLeftThumb("Run Intake");
        rightDriveController.nameRightThumb("Reverse Intake");
        rightDriveController.nameBottomThumb("Shoot");
        
        rightDriveController.getRightTopLeft().whileTrue(swerveDriveSubsystem.orchestraCommand());
        rightDriveController.nameRightTopLeft("Symphony");

        Supplier<Pose2d> targetPoseSupplier = () -> {
            PlacementLocation targetLocation =
                    FieldConstants.getNearestPlacementLocation(swerveDriveSubsystem.getPose());

            var targetPose = targetLocation.robotPlacementPose;

            Logger.log("/SwerveDriveSubsystem/TargetPose", targetPose);
            return targetPose;
        };

        Supplier<Pose2d> targetAimPoseSupplier = () -> {
            PlacementLocation targetLocation =
                    FieldConstants.getNearestPlacementLocation(swerveDriveSubsystem.getPose());

            ArmState armState = armSubsystem.getState();
            Pose3d targetPose3d;

            switch (armState) {
                case HYBRID:
                    targetPose3d = targetLocation.getHybridPose();
                    break;
                case MID:
                    targetPose3d = targetLocation.getMidPose();
                    break;
                case HIGH:
                    targetPose3d = targetLocation.getHighPose();
                    break;
                default:
                    targetPose3d = new Pose3d(targetLocation.robotPlacementPose.plus(
                            new Transform2d(new Translation2d(), Rotation2d.fromDegrees(180))));
                    break;
            }

            var targetPose = targetPose3d
                    .toPose2d()
                    .plus(new Transform2d(new Translation2d(), Rotation2d.fromDegrees(0))); // was 180

            Logger.log("/SwerveDriveSubsystem/TargetPose", targetPose);
            return targetPose;
        };

        leftDriveController
                .getRightThumb()
                .whileTrue(new DriveToPositionCommand(swerveDriveSubsystem, targetPoseSupplier));
        leftDriveController
                .getLeftThumb()
                .whileTrue(new AimAtPoseCommand(
                        swerveDriveSubsystem,
                        targetAimPoseSupplier,
                        this::getDriveForwardAxis,
                        this::getDriveStrafeAxis));                
        // leftDriveController
        //         .getBottomThumb()
        //         .whileTrue(new AssistedDriveToPositionCommand(
        //                 swerveDriveSubsystem, targetPoseSupplier, this::getDriveForwardAxis));
        leftDriveController
                .getBottomThumb()
                .whileTrue(swerveDriveSubsystem.cardinalDriveCommand(
                        this::getDriveForwardAxis,
                        this::getDriveStrafeAxis,
                        this::getCardinalXRotationAxis,
                        this::getCardinalYRotationAxis));
        leftDriveController.nameRightThumb("Drive to Pose");
        leftDriveController.nameLeftThumb("Aim at Pose");
        // leftDriveController.nameBottomThumb("Assisted Drive");
        leftDriveController.nameBottomThumb("Cardinal Driving");

        /* Set operator controller bindings */
        operatorController.getA().onTrue(runOnce(armSubsystem::setHybrid, armSubsystem));
        operatorController.getB().onTrue(runOnce(armSubsystem::setMid, armSubsystem));
        operatorController.getY().onTrue(runOnce(armSubsystem::setHigh, armSubsystem));
        operatorController.getX().onTrue(runOnce(armSubsystem::setAwaitingDeployment, armSubsystem));
        operatorController.nameA("Place Hybrid");
        operatorController.nameB("Place Mid");
        operatorController.nameY("Place High");
        operatorController.nameX("Protect Arm");

        operatorController.getBack().onTrue(runOnce(armSubsystem::setNetworkTablesMode, armSubsystem));
        operatorController.nameBack("Arm Test Mode");

        // Manual arm controls, no sussy stuff here
        operatorController
                .getDPadDown()
                .onTrue(armSubsystem.armSequence(ArmState.AWAITING_DEPLOYMENT, ArmState.HYBRID_MANUAL));
        operatorController.getDPadLeft().onTrue(runOnce(armSubsystem::setAwaitingDeployment, armSubsystem));
        operatorController.getDPadUp().onTrue(runOnce(armSubsystem::setHighManual, armSubsystem));
        operatorController.getDPadRight().onTrue(runOnce(armSubsystem::setMidManual, armSubsystem));
        operatorController.nameDPadDown("Hybrid Manual");
        operatorController.nameDPadLeft("Awaiting Deployment");
        operatorController.nameDPadUp("High Manual");
        operatorController.nameDPadRight("Pickup");

        operatorController
                .getRightTrigger()
                .whileTrue(run(
                        () -> LightsSubsystem.LEDSegment.MainStrip.setColor(LightsSubsystem.yellow), lightsSubsystem));
        operatorController
                .getLeftTrigger()
                .whileTrue(run(
                        () -> LightsSubsystem.LEDSegment.MainStrip.setColor(LightsSubsystem.purple), lightsSubsystem));
        operatorController.nameRightTrigger("Indicate Cone");
        operatorController.nameLeftTrigger("Indicate Cube");

        rightDriveController.sendButtonNamesToNT();
        leftDriveController.sendButtonNamesToNT();
        operatorController.sendButtonNamesToNT();
    }

    public Command getAutonomousCommand() {
        return autonomousManager.getAutonomousCommand();
    }

    public double getDriveForwardAxis() {
        return forwardRateLimiter.calculate(-square(deadband(leftDriveController.getYAxis().getRaw(), 0.05)) * Constants.SwerveConstants.maxSpeed);
    }

    public double getDriveStrafeAxis() {
        return strafeRateLimiter.calculate(-square(deadband(leftDriveController.getXAxis().getRaw(), 0.05)) * Constants.SwerveConstants.maxSpeed);
    }

    public double getDriveRotationAxis() {
        return -square(deadband(rightDriveController.getXAxis().getRaw(), 0.05))
                * Constants.SwerveConstants.maxAngularVelocity
                * 0.75;
    }

    public double getCardinalXRotationAxis() {
        var value = deadband(rightDriveController.getXAxis().getRaw(), 0.15);

        if (value == 0.0) return 0.0;

        return value > 0 ? 1 : -1;
    }

    public double getCardinalYRotationAxis() {
        var value = deadband(rightDriveController.getYAxis().getRaw(), 0.15);

        if (value == 0.0) return 0.0;

        return value > 0 ? 1 : -1;
    }

    private static double deadband(double value, double tolerance) {
        if (Math.abs(value) < tolerance) return 0.0;

        return Math.copySign(value, (value - tolerance) / (1.0 - tolerance));
    }

    private static double square(double value) {
        return Math.copySign(value * value, value);
    }

    public SwerveDriveSubsystem getSwerveDriveSubsystem() {
        return swerveDriveSubsystem;
    }

    public LightsSubsystem getLightsSubsystem() {
        return lightsSubsystem;
    }

    public GripperSubsystem getGripperSubsystem() {
        return gripperSubsystem;
    }

    public IntakeSubsystem getIntakeSubsystem() {
        return intakeSubsystem;
    }

    // public VisionSubsystem getVisionSubsystem() {
    //     return visionSubsystem;
    // }

    public ArmSubsystem getArmSubsystem() {
        return armSubsystem;
    }
}
