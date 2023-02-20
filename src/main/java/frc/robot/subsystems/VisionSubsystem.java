package frc.robot.subsystems;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.logging.LoggedReceiver;
import frc.lib.logging.Logger;
import frc.lib.vision.TimestampedPose;
import frc.robot.Constants.FieldConstants;
import frc.robot.Constants.VisionConstants;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.photonvision.EstimatedRobotPose;
import org.photonvision.PhotonCamera;
import org.photonvision.PhotonPoseEstimator;
import org.photonvision.PhotonPoseEstimator.PoseStrategy;

public class VisionSubsystem extends SubsystemBase {
    private PhotonCamera camera = new PhotonCamera(VisionConstants.photonCameraName);
    private PhotonPoseEstimator photonPoseEstimator = new PhotonPoseEstimator(
            FieldConstants.APRIL_TAG_FIELD_LAYOUT,
            PoseStrategy.MULTI_TAG_PNP,
            camera,
            VisionConstants.photonRobotToCamera);

    private LimelightMode limelightMode = LimelightMode.APRILTAG;

    private LoggedReceiver limelightHasTargetReceiver = Logger.receive("/limelight/tv", 0.0);
    private LoggedReceiver limelightTXReceiver = Logger.receive("/limelight/tx", 0.0);
    private LoggedReceiver limelightTYReceiver = Logger.receive("/limelight/ty", 0.0);
    private LoggedReceiver limelightApriltagIDReceiver = Logger.receive("/limelight/tid", -1);
    private LoggedReceiver limelightLatencyReceiver = Logger.receive("/limelight/tl", 0.0);
    private LoggedReceiver botposeRedReceiver = Logger.receive("/limelight/botpose_wpired", new double[] {});
    private LoggedReceiver botposeBlueReceiver = Logger.receive("/limelight/botpose_wpiblue", new double[] {});

    private Optional<TimestampedPose> LLApriltagEstimate = Optional.empty();
    private Optional<EstimatedRobotPose> photonVisionEstimate = Optional.empty();
    private Optional<Translation2d> LLRobotRelativeRetroreflectiveEstimate = Optional.empty();
    private Optional<Pose2d> LLMLFieldPoseEstimate = Optional.empty();
    private Optional<Pose2d> validLLMLFieldPoseEstimate = Optional.empty();

    private double lastApriltagUpdateTimestamp = Timer.getFPGATimestamp();

    private BiConsumer<Pose2d, Double> addVisionMeasurement;
    private Supplier<Pose2d> robotPoseSupplier;

    private static boolean visionDisabled = false;

    public VisionSubsystem(BiConsumer<Pose2d, Double> addVisionMeasurement, Supplier<Pose2d> robotPoseSupplier) {
        this.addVisionMeasurement = addVisionMeasurement;
        this.robotPoseSupplier = robotPoseSupplier;
        setLimelightMode(limelightMode);
    }

    @Override
    public void periodic() {
        /* Use limelight apriltag estimate to update robot pose estimator */
        LLApriltagEstimate = calculateLLApriltagEstimate();

        if (LLApriltagEstimate.isPresent()) {
            addVisionPoseEstimate(LLApriltagEstimate.get());

            Logger.log(
                    "/VisionSubsystem/LLApriltagPose",
                    LLApriltagEstimate.get().estimatedPose.toPose2d());

            lastApriltagUpdateTimestamp = Timer.getFPGATimestamp();
        }

        /* Use photonvision apriltag estimate to update robot pose estimator */
        photonVisionEstimate = calculatePhotonVisionEstimate();

        if (photonVisionEstimate.isPresent()) {
            addVisionPoseEstimate(photonVisionEstimate.get());

            Logger.log(
                    "/VisionSubsystem/photonVisionPose",
                    photonVisionEstimate.get().estimatedPose.toPose2d());

            lastApriltagUpdateTimestamp = Timer.getFPGATimestamp();
        }

        /* Estimate the location of the retroreflective target */
        LLRobotRelativeRetroreflectiveEstimate = calculateLLRobotRelativeRetroreflectiveEstimate();

        if (LLRobotRelativeRetroreflectiveEstimate.isPresent()) {
            Logger.log(
                    "/VisionSubsystem/LLRobotRelativeRetroreflective",
                    new Pose2d(LLRobotRelativeRetroreflectiveEstimate.get(), new Rotation2d()));
        }

        /* Estimate the location of a game piece detected by ML */
        LLMLFieldPoseEstimate = calculateLLMLFieldPoseEstimate();

        if (LLMLFieldPoseEstimate.isPresent()) {
            validLLMLFieldPoseEstimate = LLMLFieldPoseEstimate;

            Logger.log("/VisionSubsystem/LLMLFieldPoseEstimate", LLMLFieldPoseEstimate.get());
        }

        // Send the time since the last apriltag update to the dashboard
        Logger.log("/VisionSubsystem/Last Update", Timer.getFPGATimestamp() - lastApriltagUpdateTimestamp);
    }

    public void setLimelightMode(LimelightMode limelightMode) {
        this.limelightMode = limelightMode;

        Logger.log("/limelight/pipeline", limelightMode.pipelineNumber);
    }

    public LimelightMode getLimelightMode() {
        return this.limelightMode;
    }

    private boolean limelightHasTarget() {
        return limelightHasTargetReceiver.getInteger() == 1;
    }

    private boolean limelightHasApriltag() {
        return limelightApriltagIDReceiver.getInteger() != -1;
    }

    private void addVisionPoseEstimate(EstimatedRobotPose estimate) {
        addVisionMeasurement.accept(estimate.estimatedPose.toPose2d(), estimate.timestampSeconds);
    }

    private void addVisionPoseEstimate(TimestampedPose estimate) {
        addVisionMeasurement.accept(estimate.estimatedPose.toPose2d(), estimate.timestampSeconds);
    }

    public boolean hasPhotonVisionEstimate() {
        return photonVisionEstimate.isPresent();
    }

    public Optional<EstimatedRobotPose> getPhotonVisionEstimate() {
        return photonVisionEstimate;
    }

    private Optional<EstimatedRobotPose> calculatePhotonVisionEstimate() {
        if (visionDisabled) return Optional.empty();

        // Set the reference pose to the current estimated pose from the swerve drive subsystem
        photonPoseEstimator.setReferencePose(robotPoseSupplier.get());
        return photonPoseEstimator.update();
    }

    public boolean hasLLRobotRelativeRetroflectiveEstimate() {
        return LLRobotRelativeRetroreflectiveEstimate.isPresent();
    }

    public Optional<Translation2d> getLLRobotRelativeRetroflectiveEstimate() {
        return LLRobotRelativeRetroreflectiveEstimate;
    }

    private Optional<Translation2d> calculateLLRobotRelativeRetroreflectiveEstimate() {
        if (visionDisabled) return Optional.empty();

        if (!limelightHasTarget()
                || (limelightMode != LimelightMode.RETROREFLECTIVEMAIN
                        && limelightMode != LimelightMode.RETROREFLECTIVESECOND)) return Optional.empty();

        double limelightTX = limelightTXReceiver.getDouble();
        double limelightTY = limelightTYReceiver.getDouble();

        double retroreflectiveHeight;

        if (limelightTY < 0) {
            retroreflectiveHeight = VisionConstants.lowerRetroreflectiveHeight;
        } else {
            retroreflectiveHeight = VisionConstants.upperRetroreflectiveHeight;
        }

        var distance = (retroreflectiveHeight - VisionConstants.limelightRobotToCamera.getZ())
                / Math.tan(VisionConstants.limelightCameraToRobot.getRotation().getY() + Math.toRadians(limelightTY));

        Translation2d cameraToRetroreflective = new Translation2d(
                distance,
                Rotation2d.fromDegrees(-limelightTX)
                        .plus(robotPoseSupplier.get().getRotation()));

        Translation2d cameraToRobot = new Translation2d(
                VisionConstants.limelightCameraToRobot.getX(), VisionConstants.limelightCameraToRobot.getY());

        Translation2d relativeTarget = cameraToRetroreflective.minus(cameraToRobot);

        return Optional.of(relativeTarget);
    }

    public boolean hasLLMLFieldPoseEstimate() {
        return LLMLFieldPoseEstimate.isPresent();
    }

    public Optional<Pose2d> getLLMLFieldPoseEstimate() {
        return LLMLFieldPoseEstimate;
    }

    public Supplier<Pose2d> getMLPoseSupplier() {
        return () -> validLLMLFieldPoseEstimate.get();
    }

    private Optional<Pose2d> calculateLLMLFieldPoseEstimate() {
        if (visionDisabled) return Optional.empty();

        if (!limelightHasTarget() || limelightMode != LimelightMode.ML) return Optional.empty();

        // Use bottom edge if possible
        double limelightTX = limelightTXReceiver.getDouble();
        double limelightTY = limelightTYReceiver.getDouble();

        // Cones and cubes are always picked up from the floor
        double targetHeight = 0;

        var distance = (targetHeight - VisionConstants.limelightRobotToCamera.getZ())
                / Math.tan(VisionConstants.limelightCameraToRobot.getRotation().getY() + Math.toRadians(limelightTY));

        var robotPose = robotPoseSupplier.get();

        Translation2d cameraToMLTarget =
                new Translation2d(distance, Rotation2d.fromDegrees(-limelightTX).plus(robotPose.getRotation()));

        Translation2d cameraToRobot = new Translation2d(
                VisionConstants.limelightCameraToRobot.getX(), VisionConstants.limelightCameraToRobot.getY());

        Translation2d relativeTarget = cameraToMLTarget.minus(cameraToRobot);

        Pose2d fieldPose = new Pose2d(relativeTarget, robotPose.getRotation().plus(relativeTarget.getAngle()))
                .relativeTo(robotPose);

        return Optional.of(fieldPose);
    }

    public boolean hasLLApriltagEstimate() {
        return LLApriltagEstimate.isPresent();
    }

    public Optional<TimestampedPose> getLLApriltagEstimate() {
        return LLApriltagEstimate;
    }

    private Optional<TimestampedPose> calculateLLApriltagEstimate() {
        if (visionDisabled || getLimelightMode() != LimelightMode.APRILTAG) return Optional.empty();

        // gets the botpose array from the limelight and a timestamp
        double[] botposeArray = DriverStation.getAlliance() == Alliance.Red
                ? botposeRedReceiver.getDoubleArray()
                : botposeBlueReceiver.getDoubleArray(); // double[] {x, y, z, roll, pitch, yaw}
        double timestamp = Timer.getFPGATimestamp() - limelightLatencyReceiver.getDouble() / 1000.0;

        // if botpose exists and the limelight has an april tag, it adds the pose to our kalman filter
        if (limelightHasApriltag() && botposeArray.length == 6) {
            Pose3d botPose = new Pose3d(
                            botposeArray[0],
                            botposeArray[1],
                            botposeArray[2],
                            new Rotation3d(
                                    Math.toRadians(botposeArray[3]),
                                    Math.toRadians(botposeArray[4]),
                                    Math.toRadians(botposeArray[5])))
                    .transformBy(VisionConstants.limelightCameraToRobot);
            return Optional.of(new TimestampedPose(botPose, timestamp));
        } else {
            return Optional.empty();
        }
    }

    public enum LimelightMode {
        APRILTAG(0),
        RETROREFLECTIVEMAIN(1),
        RETROREFLECTIVESECOND(2),
        ML(3);

        public int pipelineNumber;

        private LimelightMode(int pipelineNumber) {
            this.pipelineNumber = pipelineNumber;
        }
    }
}
