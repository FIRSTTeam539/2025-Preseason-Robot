// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.swervedrive;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.commands.PathPlannerAuto;
import com.pathplanner.lib.path.PathConstraints;
import com.pathplanner.lib.path.PathPlannerPath;
import com.pathplanner.lib.util.HolonomicPathFollowerConfig;
import com.pathplanner.lib.util.ReplanningConfig;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import frc.robot.Constants.Auton;
import frc.robot.Constants.AutonConstants;
import frc.robot.subsystems.LimelightSubsystem;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.trajectory.Trajectory;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine.Config;
import java.io.File;
import java.util.function.DoubleSupplier;
import swervelib.SwerveController;
import swervelib.SwerveDrive;
import swervelib.SwerveDriveTest;
import swervelib.math.SwerveMath;
import swervelib.parser.SwerveControllerConfiguration;
import swervelib.parser.SwerveDriveConfiguration;
import swervelib.parser.SwerveParser;
import swervelib.telemetry.SwerveDriveTelemetry;
import swervelib.telemetry.SwerveDriveTelemetry.TelemetryVerbosity;

public class SwerveSubsystem extends SubsystemBase
{

  /**
   * Swerve drive object.
   */
  private final SwerveDrive swerveDrive;
  /**
   * Maximum speed of the robot in meters per second, used to limit acceleration.
   */
  public        double      maximumSpeed = 3.8;

  /**
   * Initialize {@link SwerveDrive} with the directory provided.
   *
   * @param directory Directory of swerve drive config files.
   */
  public SwerveSubsystem(File directory)
  {
    // Angle conversion factor is 360 / (GEAR RATIO * ENCODER RESOLUTION)
    //  In this case the gear ratio is 12.8 motor revolutions per wheel rotation.
    //  The encoder resolution per motor revolution is 1 per motor revolution.
    double angleConversionFactor = SwerveMath.calculateDegreesPerSteeringRotation(12.8);
    // Motor conversion factor is (PI * WHEEL DIAMETER IN METERS) / (GEAR RATIO * ENCODER RESOLUTION).
    //  In this case the wheel diameter is 4 inches, which must be converted to meters to get meters/second.
    //  The gear ratio is 6.75 motor revolutions per wheel rotation.
    //  The encoder resolution per motor revolution is 1 per motor revolution.
    double driveConversionFactor = SwerveMath.calculateMetersPerRotation(Units.inchesToMeters(4), 6.75);
    System.out.println("\"conversionFactor\": {");
    System.out.println("\t\"angle\": " + angleConversionFactor + ",");
    System.out.println("\t\"drive\": " + driveConversionFactor);
    System.out.println("}");

    // Configure the Telemetry before creating the SwerveDrive to avoid unnecessary objects being created.
    SwerveDriveTelemetry.verbosity = TelemetryVerbosity.HIGH;
    try
    {
      swerveDrive = new SwerveParser(directory).createSwerveDrive(maximumSpeed);
      swerveDrive.swerveController.setMaximumAngularVelocity(Units.degreesToRadians(287));
      // Alternative method if you don't want to supply the conversion factor via JSON files.
      // swerveDrive = new SwerveParser(directory).createSwerveDrive(maximumSpeed, angleConversionFactor, driveConversionFactor);
    } catch (Exception e)
    {
      throw new RuntimeException(e);
    }
    swerveDrive.setHeadingCorrection(false); // Heading correction should only be used while controlling the robot via angle.
    swerveDrive.setCosineCompensator(!SwerveDriveTelemetry.isSimulation); // Disables cosine compensation for simulations since it causes discrepancies not seen in real life.
    setupPathPlanner();
  }

  /**
   * Construct the swerve drive.
   *
   * @param driveCfg      SwerveDriveConfiguration for the swerve.
   * @param controllerCfg Swerve Controller.
   */
  public SwerveSubsystem(SwerveDriveConfiguration driveCfg, SwerveControllerConfiguration controllerCfg)
  {
    swerveDrive = new SwerveDrive(driveCfg, controllerCfg, maximumSpeed);
  }

  /**
   * Setup AutoBuilder for PathPlanner.
   */
  public void setupPathPlanner()
  {
    AutoBuilder.configureHolonomic(
        this::getPose, // Robot pose supplier
        this::resetOdometry, // Method to reset odometry (will be called if your auto has a starting pose)
        this::getRobotVelocity, // ChassisSpeeds supplier. MUST BE ROBOT RELATIVE
        this::setChassisSpeeds, // Method that will drive the robot given ROBOT RELATIVE ChassisSpeeds
        new HolonomicPathFollowerConfig( // HolonomicPathFollowerConfig, this should likely live in your Constants class
                                         AutonConstants.TRANSLATION_PID,
                                         // Translation PID constants
                                         AutonConstants.ANGLE_PID,
                                         // Rotation PID constants
                                         5.36,
                                         // Max module speed, in m/s
                                         swerveDrive.swerveDriveConfiguration.getDriveBaseRadiusMeters(),
                                         // Drive base radius in meters. Distance from robot center to furthest module.
                                         new ReplanningConfig()
                                         // Default path replanning config. See the API for the options here
        ),
        () -> {
          // Boolean supplier that controls when the path will be mirrored for the red alliance
          // This will flip the path being followed to the red side of the field.
          // THE ORIGIN WILL REMAIN ON THE BLUE SIDE
          var alliance = DriverStation.getAlliance();
          return alliance.isPresent() ? alliance.get() == DriverStation.Alliance.Red : false;
        },
        this // Reference to this subsystem to set requirements
                                  );
  }

  /**
   * Get the path follower with events.
   *
   * @param pathName       PathPlanner path name.
   * @return {@link AutoBuilder#followPath(PathPlannerPath)} path command.
   */
  public Command getAutonomousCommand(String pathName)
  {
    // Create a path following command using AutoBuilder. This will also trigger event markers.
    return new PathPlannerAuto(pathName);
  }

  /**
   * Use PathPlanner Path finding to go to a point on the field.
   *
   * @param pose Target {@link Pose2d} to go to.
   * @return PathFinding command
   */
  public Command driveToPose(Pose2d pose)
  {
// Create the constraints to use while pathfinding
    PathConstraints constraints = new PathConstraints(
        swerveDrive.getMaximumVelocity(), 4.0,
        swerveDrive.getMaximumAngularVelocity(), Units.degreesToRadians(720));

// Since AutoBuilder is configured, we can use it to build pathfinding commands
    return AutoBuilder.pathfindToPose(
        pose,
        constraints,
        0.0, // Goal end velocity in meters/sec
        0.0 // Rotation delay distance in meters. This is how far the robot should travel before attempting to rotate.
                                     );
  }

  /**
   * Command to drive the robot using translative values and heading as a setpoint.
   *
   * @param translationX Translation in the X direction. Cubed for smoother controls.
   * @param translationY Translation in the Y direction. Cubed for smoother controls.
   * @param headingX     Heading X to calculate angle of the joystick.
   * @param headingY     Heading Y to calculate angle of the joystick.
   * @return Drive command.
   */
  public Command driveCommand(DoubleSupplier translationX, DoubleSupplier translationY, DoubleSupplier headingX,
                              DoubleSupplier headingY)
  {
    // swerveDrive.setHeadingCorrection(true); // Normally you would want heading correction for this kind of control.
    return run(() -> {
      double xInput = Math.pow(translationX.getAsDouble(), 3); // Smooth controll out
      double yInput = Math.pow(translationY.getAsDouble(), 3); // Smooth controll out
      // Make the robot move
      driveFieldOriented(swerveDrive.swerveController.getTargetSpeeds(xInput, yInput,
                                                                      headingX.getAsDouble(),
                                                                      headingY.getAsDouble(),
                                                                      swerveDrive.getOdometryHeading().getRadians(),
                                                                      swerveDrive.getMaximumVelocity()));
    });
  }

  /**
   * Command to drive the robot using translative values and heading as a setpoint.
   *
   * @param translationX Translation in the X direction.
   * @param translationY Translation in the Y direction.
   * @param rotation     Rotation as a value between [-1, 1] converted to radians.
   * @return Drive command.
   */
  public Command simDriveCommand(DoubleSupplier translationX, DoubleSupplier translationY, DoubleSupplier rotation)
  {
    // swerveDrive.setHeadingCorrection(true); // Normally you would want heading correction for this kind of control.
    return run(() -> {
      // Make the robot move
      driveFieldOriented(swerveDrive.swerveController.getTargetSpeeds(translationX.getAsDouble(),
                                                                      translationY.getAsDouble(),
                                                                      rotation.getAsDouble() * Math.PI,
                                                                      swerveDrive.getOdometryHeading().getRadians(),
                                                                      swerveDrive.getMaximumVelocity()));
    });
  }

  /**
   * Command to characterize the robot drive motors using SysId
   *
   * @return SysId Drive Command
   */
  public Command sysIdDriveMotorCommand()
  {
    return SwerveDriveTest.generateSysIdCommand(
        SwerveDriveTest.setDriveSysIdRoutine(
            new Config(),
            this, swerveDrive, 12),
        3.0, 5.0, 3.0);
  }

  /**
   * Command to characterize the robot angle motors using SysId
   *
   * @return SysId Angle Command
   */
  public Command sysIdAngleMotorCommand()
  {
    return SwerveDriveTest.generateSysIdCommand(
        SwerveDriveTest.setAngleSysIdRoutine(
            new Config(),
            this, swerveDrive),
        3.0, 5.0, 3.0);
  }

  /**
   * Command to drive the robot using translative values and heading as angular velocity.
   *
   * @param translationX     Translation in the X direction. Cubed for smoother controls.
   * @param translationY     Translation in the Y direction. Cubed for smoother controls.
   * @param angularRotationX Angular velocity of the robot to set. Cubed for smoother controls.
   * @return Drive command.
   */
  public Command driveCommand(DoubleSupplier translationX, DoubleSupplier translationY, DoubleSupplier angularRotationX)
  {
    return run(() -> {
      // Make the robot move
      swerveDrive.drive(new Translation2d(Math.pow(translationX.getAsDouble(), 3) * swerveDrive.getMaximumVelocity(),
                                          Math.pow(translationY.getAsDouble(), 3) * swerveDrive.getMaximumVelocity()),
                        Math.pow(angularRotationX.getAsDouble(), 3) * swerveDrive.getMaximumAngularVelocity(),
                        true,
                        false);
    });
  }

  /**
   * The primary method for controlling the drivebase.  Takes a {@link Translation2d} and a rotation rate, and
   * calculates and commands module states accordingly.  Can use either open-loop or closed-loop velocity control for
   * the wheel velocities.  Also has field- and robot-relative modes, which affect how the translation vector is used.
   *
   * @param translation   {@link Translation2d} that is the commanded linear velocity of the robot, in meters per
   *                      second. In robot-relative mode, positive x is torwards the bow (front) and positive y is
   *                      torwards port (left).  In field-relative mode, positive x is away from the alliance wall
   *                      (field North) and positive y is torwards the left wall when looking through the driver station
   *                      glass (field West).
   * @param rotation      Robot angular rate, in radians per second. CCW positive.  Unaffected by field/robot
   *                      relativity.
   * @param fieldRelative Drive mode.  True for field-relative, false for robot-relative.
   */
  public void drive(Translation2d translation, double rotation, boolean fieldRelative)
  {
    swerveDrive.drive(translation,
                      rotation,
                      fieldRelative,
                      false); // Open loop is disabled since it shouldn't be used most of the time.
  }

  /**
   * Drive the robot given a chassis field oriented velocity.
   *
   * @param velocity Velocity according to the field.
   */
  public void driveFieldOriented(ChassisSpeeds velocity)
  {
    swerveDrive.driveFieldOriented(velocity);
  }

  /**
   * Drive according to the chassis robot oriented velocity.
   *
   * @param velocity Robot oriented {@link ChassisSpeeds}
   */
  public void drive(ChassisSpeeds velocity)
  {
    swerveDrive.drive(velocity);
  }

  @Override
  public void periodic()
  {
    SmartDashboard.putNumber("robot speed feet", this.getRobotSpeedFeet());
    SmartDashboard.putNumber("angular velocitry degrees", this.getRobotAngularVelocityDegrees());
    SmartDashboard.putNumber("robot speed meters", this.getRobotSpeedMeters());
    SmartDashboard.putNumber("angular velocitry radians", this.getRobotAngularVelocityRadians());
     SmartDashboard.putNumber("Pose2d/X", this.getPose().getX());
     SmartDashboard.putNumber("Pose2d/y", this.getPose().getY());
     SmartDashboard.putNumber("Pose2d/Omega", this.getPose().getRotation().getDegrees());
  }
  /**
   * 
   * @return returns robot speed in feet per second
   */
  public double getRobotSpeedFeet(){
    return Units.metersToFeet(Math.sqrt(Math.pow(swerveDrive.getRobotVelocity().vxMetersPerSecond,2)+Math.pow(swerveDrive.getRobotVelocity().vyMetersPerSecond, 2)));
  }
    /**
   * 
   * @return returns robot speed in meters per second
   */
  public double getRobotSpeedMeters(){
    return Math.sqrt(Math.pow(swerveDrive.getRobotVelocity().vxMetersPerSecond,2)+Math.pow(swerveDrive.getRobotVelocity().vyMetersPerSecond, 2));
  }
  /**
   * 
   * @return returns angular velocity in degrees per second
   */
  public double getRobotAngularVelocityDegrees(){
    return Units.radiansToDegrees(swerveDrive.getRobotVelocity().omegaRadiansPerSecond);
  }
  /**
   * 
   * @return returns angular velocity in radians per second
   */
  public double getRobotAngularVelocityRadians(){
    return swerveDrive.getRobotVelocity().omegaRadiansPerSecond;
  }
  public double get(){
    return swerveDrive.getAccel().get().toTranslation2d().getX();
  }

//
  @Override
  public void simulationPeriodic()
  {
  }

  /**
   * Get the swerve drive kinematics object.
   *
   * @return {@link SwerveDriveKinematics} of the swerve drive.
   */
  public SwerveDriveKinematics getKinematics()
  {
    return swerveDrive.kinematics;
  }

  /**
   * Resets odometry to the given pose. Gyro angle and module positions do not need to be reset when calling this
   * method.  However, if either gyro angle or module position is reset, this must be called in order for odometry to
   * keep working.
   *
   * @param initialHolonomicPose The pose to set the odometry to
   */
  public void resetOdometry(Pose2d initialHolonomicPose)
  {
    swerveDrive.resetOdometry(initialHolonomicPose);
  }

  /**
   * Gets the current pose (position and rotation) of the robot, as reported by odometry.
   *
   * @return The robot's pose
   */
  public Pose2d getPose()
  {
    return swerveDrive.getPose();
  }


  /**
   * Set chassis speeds with closed-loop velocity control.
   *
   * @param chassisSpeeds Chassis Speeds to set.
   */
  public void setChassisSpeeds(ChassisSpeeds chassisSpeeds)
  {
    swerveDrive.setChassisSpeeds(chassisSpeeds);
  }

  /**
   * Post the trajectory to the field.
   *
   * @param trajectory The trajectory to post.
   */
  public void postTrajectory(Trajectory trajectory)
  {
    swerveDrive.postTrajectory(trajectory);
  }

  /**
   * Resets the gyro angle to zero and resets odometry to the same position, but facing toward 0.
   */
  public void zeroGyro()
  {
    swerveDrive.zeroGyro();
  }

  /**
   * Sets the drive motors to brake/coast mode.
   *
   * @param brake True to set motors to brake mode, false for coast.
   */
  public void setMotorBrake(boolean brake)
  {
    swerveDrive.setMotorIdleMode(brake);
  }

  /**
   * Gets the current yaw angle of the robot, as reported by the swerve pose estimator in the underlying drivebase.
   * Note, this is not the raw gyro reading, this may be corrected from calls to resetOdometry().
   *
   * @return The yaw angle
   */
  public Rotation2d getHeading()
  {
    return getPose().getRotation();
  }

  /**
   * Get the chassis speeds based on controller input of 2 joysticks. One for speeds in which direction. The other for
   * the angle of the robot.
   *
   * @param xInput   X joystick input for the robot to move in the X direction.
   * @param yInput   Y joystick input for the robot to move in the Y direction.
   * @param headingX X joystick which controls the angle of the robot.
   * @param headingY Y joystick which controls the angle of the robot.
   * @return {@link ChassisSpeeds} which can be sent to the Swerve Drive.
   */
  public ChassisSpeeds getTargetSpeeds(double xInput, double yInput, double headingX, double headingY)
  {
    xInput = Math.pow(xInput, 3);
    yInput = Math.pow(yInput, 3);
    return swerveDrive.swerveController.getTargetSpeeds(xInput,
                                                        yInput,
                                                        headingX,
                                                        headingY,
                                                        getHeading().getRadians(),
                                                        maximumSpeed);
  }

  /**
   * Get the chassis speeds based on controller input of 1 joystick and one angle. Control the robot at an offset of
   * 90deg.
   *
   * @param xInput X joystick input for the robot to move in the X direction.
   * @param yInput Y joystick input for the robot to move in the Y direction.
   * @param angle  The angle in as a {@link Rotation2d}.
   * @return {@link ChassisSpeeds} which can be sent to the Swerve Drive.
   */
  public ChassisSpeeds getTargetSpeeds(double xInput, double yInput, Rotation2d angle)
  {
    xInput = Math.pow(xInput, 3);
    yInput = Math.pow(yInput, 3);
    return swerveDrive.swerveController.getTargetSpeeds(xInput,
                                                        yInput,
                                                        angle.getRadians(),
                                                        getHeading().getRadians(),
                                                        maximumSpeed);
  }

  /**
   * Gets the current field-relative velocity (x, y and omega) of the robot
   *
   * @return A ChassisSpeeds object of the current field-relative velocity
   */
  public ChassisSpeeds getFieldVelocity()
  {
    return swerveDrive.getFieldVelocity();
  }

  /**
   * Gets the current velocity (x, y and omega) of the robot
   *
   * @return A {@link ChassisSpeeds} object of the current velocity
   */
  public ChassisSpeeds getRobotVelocity()
  {
    return swerveDrive.getRobotVelocity();
  }

  /**
   * Get the {@link SwerveController} in the swerve drive.
   *
   * @return {@link SwerveController} from the {@link SwerveDrive}.
   */
  public SwerveController getSwerveController()
  {
    return swerveDrive.swerveController;
  }

  /**
   * Get the {@link SwerveDriveConfiguration} object.
   *
   * @return The {@link SwerveDriveConfiguration} fpr the current drive.
   */
  public SwerveDriveConfiguration getSwerveDriveConfiguration()
  {
    return swerveDrive.swerveDriveConfiguration;
  }

  /**
   * Lock the swerve drive to prevent it from moving.
   */
  public void lock()
  {
    swerveDrive.lockPose();
  }

  /**
   * Gets the current pitch angle of the robot, as reported by the imu.
   *
   * @return The heading as a {@link Rotation2d} angle
   */
  public Rotation2d getPitch()
  {
    return swerveDrive.getPitch();
  }

  /**
   * Add a fake vision reading for testing purposes.
   */
  public void addFakeVisionReading()
  {
    swerveDrive.addVisionMeasurement(new Pose2d(3, 3, Rotation2d.fromDegrees(65)), Timer.getFPGATimestamp());
  }
  
  // public void addVisionMeasurement(LimelightSubsystem limelight, double timestamp){
  //   if (limelight.getTV()) {
  //     double xyStds;
  //     double degStds;
  //     // multiple targets detected
  //     /*if (m_visionSystem.getNumberOfTargetsVisible() >= 2) {
  //       xyStds = 0.5;
  //       degStds = 6;
  //     }
  //     // 1 target with large area and close to estimated pose
  //     else if (m_visionSystem.getBestTargetArea() > 0.8 && poseDifference < 0.5) {
  //       xyStds = 1.0;
  //       degStds = 12;
  //     }
  //     // 1 target farther away and estimated pose is close
  //     else if (m_visionSystem.getBestTargetArea() > 0.1 && poseDifference < 0.3) {
  //       xyStds = 2.0;
  //       degStds = 30;
  //     }
  //     // conditions don't match to add a vision measurement
  //     else {
  //       return;
  //     }*/
  //     if(Math.sqrt(Math.pow(this.getPose().getX()-limelight.getBotPose2d().getX(), 2)+Math.pow(this.getPose().getY()-limelight.getBotPose2d().getY(), 2))<0.5){
  //       xyStds = 2.0;
  //       degStds = 30;
  //     } else  {
  //       return;
  //     }
      
  //   swerveDrive.addVisionMeasurement(limelight.getBotPose2d_wpiBlue(), timestamp-limelight.getLatency(), VecBuilder.fill(xyStds, xyStds, Units.degreesToRadians(degStds)));
  //   }
  // }
}

// /* // Copyright (c) FIRST and other WPILib contributors.
// // Open Source Software; you can modify and/or share it under the terms of
// // the WPILib BSD license file in the root directory of this project.

// package frc.robot.subsystems.swervedrive;

// import com.pathplanner.lib.auto.AutoBuilder;
// import com.pathplanner.lib.path.PathConstraints;
// import com.pathplanner.lib.path.PathPlannerPath;
// import com.pathplanner.lib.util.HolonomicPathFollowerConfig;
// import com.pathplanner.lib.util.PIDConstants;
// import com.pathplanner.lib.util.ReplanningConfig;
// import edu.wpi.first.math.geometry.Pose2d;
// import edu.wpi.first.math.geometry.Rotation2d;
// import edu.wpi.first.math.geometry.Rotation3d;
// import edu.wpi.first.math.geometry.Translation2d;
// import edu.wpi.first.math.kinematics.ChassisSpeeds;
// import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
// import edu.wpi.first.math.numbers.N1;
// import edu.wpi.first.math.numbers.N3;
// import edu.wpi.first.math.trajectory.Trajectory;
// import edu.wpi.first.math.util.Units;
// import edu.wpi.first.util.sendable.Sendable;
// import edu.wpi.first.math.Matrix;
// import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
// import edu.wpi.first.math.numbers.N1;
// import edu.wpi.first.math.numbers.N3;
// import edu.wpi.first.wpilibj.DriverStation;
// import edu.wpi.first.wpilibj.Timer;
// import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
// import edu.wpi.first.wpilibj2.command.Command;
// import edu.wpi.first.wpilibj2.command.SubsystemBase;
// import java.io.File;
// import java.util.function.DoubleSupplier;
// import swervelib.SwerveController;
// import swervelib.SwerveDrive;
// import swervelib.SwerveDriveTest;
// import swervelib.imu.SwerveIMU;
// import swervelib.math.SwerveMath;
// import swervelib.parser.SwerveControllerConfiguration;
// import swervelib.parser.SwerveDriveConfiguration;
// import swervelib.parser.SwerveParser;
// import swervelib.telemetry.SwerveDriveTelemetry;
// import swervelib.telemetry.SwerveDriveTelemetry.TelemetryVerbosity;
// import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine.Config;
// import edu.wpi.first.wpilibj.Timer;
// import frc.robot.LimelightHelpers;
// import frc.robot.subsystems.LimelightSubsystem;

// public class SwerveSubsystem extends SubsystemBase
// {

//   /**
//    * Swerve drive object.
//    */
//   private final SwerveDrive swerveDrive;

//   //private final LimelightSubsystem limelight;
//   /**
//    * Maximum speed of the robot in meters per second, used to limit acceleration.
//    */
//   public        double      maximumSpeed = Units.feetToMeters(12.5);

//   /**
//    * Initialize {@link SwerveDrive} with the directory provided.
//    *
//    * @param directory Directory of swerve drive config files.
//    */
//   public SwerveSubsystem(File directory)//, LimelightSubsystem limelight)
//   {
//     //this.limelight =limelight;
//     // Angle conversion factor is 360 / (GEAR RATIO * ENCODER RESOLUTION)
//     //  In this case the gear ratio is 12.8 motor revolutions per wheel rotation.
//     //  The encoder resolution per motor revolution is 1 per motor revolution.
//     double angleConversionFactor = SwerveMath.calculateDegreesPerSteeringRotation(150/7, 1);
//     // Motor conversion factor is (PI * WHEEL DIAMETER IN METERS) / (GEAR RATIO * ENCODER RESOLUTION).
//     //  In this case the wheel diameter is 4 inches, which must be converted to meters to get meters/second.
//     //  The gear ratio is 6.75 motor revolutions per wheel rotation.
//     // The encoder resolution per motor revolution is 1 per motor revolution.
//     double driveConversionFactor = SwerveMath.calculateMetersPerRotation(Units.inchesToMeters(4), 8.14, 42);
//     System.out.println("\"conversionFactor\": {");
//     System.out.println("\t\"angle\": " + angleConversionFactor + ",");
//     System.out.println("\t\"drive\": " + driveConversionFactor);
//     System.out.println("}");

//     // Configure the Telemetry before creating the SwerveDrive to avoid unnecessary objects being created.
//     SwerveDriveTelemetry.verbosity = TelemetryVerbosity.HIGH;
//     //SmartDashboard.putNumber("Velocity", this.getRobotSpeed());
//     SmartDashboard.putNumber("Angular Rotation", this.getRobotAngularVelocity());

//     try
//     {
//       //swerveDrive = new SwerveParser(directory).createSwerveDrive(maximumSpeed);
//       // Alternative method if you don't want to supply the conversion factor via JSON files.
//       this.swerveDrive = new SwerveParser(directory).createSwerveDrive(maximumSpeed, angleConversionFactor, driveConversionFactor);
//     } catch (Exception e)
//     {
//       System.out.println("too bad");
//       throw new RuntimeException(e);
//     }
//     swerveDrive.setHeadingCorrection(false); // Heading correction should only be used while controlling the robot via angle.
//     swerveDrive.setMotorIdleMode(true);

//     //swerveDrive.swerveController.addSlewRateLimiters(); // possibly add later
    
//     setupPathPlanner();
//     //swerveDrive.addVisionMeasurement(LimelightHelpers.getBotPose2d(), driveConversionFactor);
//   }

//   /**
//    * Setup AutoBuilder for PathPlanner.
//    */
//   public void setupPathPlanner()
//   {
//     AutoBuilder.configureHolonomic(
//         this::getPose, // Robot pose supplier
//         this::resetOdometry, // Method to reset odometry (will be called if your auto has a starting pose)
//         this::getRobotVelocity, // ChassisSpeeds supplier. MUST BE ROBOT RELATIVE
//         this::setChassisSpeeds, // Method that will drive the robot given ROBOT RELATIVE ChassisSpeeds
//         new HolonomicPathFollowerConfig( // HolonomicPathFollowerConfig, this should likely live in your Constants class
//                                          new PIDConstants(5.0, 0.0, 0.0),
//                                          // Translation PID constants
//                                          new PIDConstants(swerveDrive.swerveController.config.headingPIDF.p,
//                                                           swerveDrive.swerveController.config.headingPIDF.i,
//                                                           swerveDrive.swerveController.config.headingPIDF.d),
//                                          // Rotation PID constants
//                                          4.5,
//                                          // Max module speed, in m/s
//                                          swerveDrive.swerveDriveConfiguration.getDriveBaseRadiusMeters(),
//                                          // Drive base radius in meters. Distance from robot center to furthest module.
//                                          new ReplanningConfig()
//                                          // Default path replanning config. See the API for the options here
//         ),
//         () -> {
//           // Boolean supplier that controls when the path will be mirrored for the red alliance
//           // This will flip the path being followed to the red side of the field.
//           // THE ORIGIN WILL REMAIN ON THE BLUE SIDE
//           var alliance = DriverStation.getAlliance();
//           return alliance.isPresent() ? alliance.get() == DriverStation.Alliance.Red : false;
//         },
//         this // Reference to this subsystem to set requirements
//                                   );
//   }

//   /**
//    * 
//    * @return returns the current robot speed in meters per second
//    */
//   /*public double getRobotSpeed(){
//     return Math.sqrt(Math.pow(swerveDrive.getRobotVelocity().vxMetersPerSecond,2)+Math.pow(swerveDrive.getRobotVelocity().vyMetersPerSecond, 2));
//   }*/

//   /**
//    * 
//    * @return returns the current angular Velocity in meters per second
//    */
//   public double getRobotAngularVelocity(){
//     return swerveDrive.getRobotVelocity().omegaRadiansPerSecond;
//   }

//   /**
//    * Get the path follower with events.
//    *
//    * @param pathName       PathPlanner path name.
//    * @param setOdomToStart Set the odometry position to the start of the path.
//    * @return {@link AutoBuilder#followPath(PathPlannerPath)} path command.
//    */
//   public Command getAutonomousCommand(String pathName, boolean setOdomToStart)
//   {
//     // Load the path you want to follow using its name in the GUI
//     PathPlannerPath path = PathPlannerPath.fromPathFile(pathName);

//     if (setOdomToStart)
//     {
//       resetOdometry(new Pose2d(path.getPoint(0).position, getHeading()));
//     }

//     // Create a path following command using AutoBuilder. This will also trigger event markers.
//       return AutoBuilder.followPath(path);
//   }

//     /**
//    * Use PathPlanner Path finding to go to a point on the field.
//    *
//    * @param pose Target {@link Pose2d} to go to.
//    * @return PathFinding command
//    */
//   public Command driveToPose(Pose2d pose)
//   {
//     // Create the constraints to use while pathfinding
//     PathConstraints constraints = new PathConstraints(
//         swerveDrive.getMaximumVelocity(), 4.0,
//         swerveDrive.getMaximumAngularVelocity(), Units.degreesToRadians(720));

//     // Since AutoBuilder is configured, we can use it to build pathfinding commands
//     return AutoBuilder.pathfindToPose(
//         pose,
//         constraints,
//         0.0, // Goal end velocity in meters/sec
//         0.0 // Rotation delay distance in meters. This is how far the robot should travel before attempting to rotate.
//                                      );
//   }

//   /**
//    * Command to drive the robot using translative values and heading as a setpoint.
//    *
//    * @param translationX Translation in the X direction.
//    * @param translationY Translation in the Y direction.
//    * @param headingX     Heading X to calculate angle of the joystick.
//    * @param headingY     Heading Y to calculate angle of the joystick.
//    * @return Drive command.
//    */
//   public Command driveCommand(DoubleSupplier translationX, DoubleSupplier translationY, DoubleSupplier headingX,
//                               DoubleSupplier headingY)
//   {
//     return run(() -> {
//       // Make the robot move
//       driveFieldOriented(getTargetSpeeds(translationX.getAsDouble(), translationY.getAsDouble(),
//                                          headingX.getAsDouble(),
//                                          headingY.getAsDouble()));
//     });
//   }

//   /**
//    * Command to drive the robot using translative values and heading as angular velocity.
//    *
//    * @param translationX     Translation in the X direction.
//    * @param translationY     Translation in the Y direction.
//    * @param angularRotationX Rotation of the robot to set
//    * @return Drive command.
//    */
//   public Command driveCommand(DoubleSupplier translationX, DoubleSupplier translationY, DoubleSupplier angularRotationX)
//   {
//     return run(() -> {
//       // Make the robot move
//       swerveDrive.drive(new Translation2d(translationX.getAsDouble() * maximumSpeed, translationY.getAsDouble()),
//                         angularRotationX.getAsDouble() * swerveDrive.swerveController.config.maxAngularVelocity,
//                         true,
//                         false);
//     });
//   }

//     /**
//    * Command to drive the robot using translative values and heading as a setpoint.
//    *
//    * @param translationX Translation in the X direction.
//    * @param translationY Translation in the Y direction.
//    * @param rotation     Rotation as a value between [-1, 1] converted to radians.
//    * @return Drive command.
//    */
//   public Command simDriveCommand(DoubleSupplier translationX, DoubleSupplier translationY, DoubleSupplier rotation)
//   {
//     // swerveDrive.setHeadingCorrection(true); // Normally you would want heading correction for this kind of control.
//     return run(() -> {
//       // Make the robot move
//       driveFieldOriented(swerveDrive.swerveController.getTargetSpeeds(translationX.getAsDouble(),
//                                                                       translationY.getAsDouble(),
//                                                                       rotation.getAsDouble() * Math.PI,
//                                                                       swerveDrive.getYaw().getRadians(),
//                                                                       swerveDrive.getMaximumVelocity()));
//     });
//   }

//     /**
//    * Command to characterize the robot drive motors using SysId
//    * @return SysId Drive Command
//    */
//   public Command sysIdDriveMotorCommand() {
//     return SwerveDriveTest.generateSysIdCommand(
//           SwerveDriveTest.setDriveSysIdRoutine(
//               new Config(),
//               this, swerveDrive, 12),
//           3.0, 5.0, 3.0);
//   }

//   /**
//  * Command to characterize the robot angle motors using SysId
//  * @return SysId Angle Command
//  */
//   public Command sysIdAngleMotorCommand() {
//     return SwerveDriveTest.generateSysIdCommand(
//       SwerveDriveTest.setAngleSysIdRoutine(
//         new Config(),
//         this, swerveDrive),
//   3.0, 5.0, 3.0);
//   }

//   /**
//    * Construct the swerve drive.
//    *
//    * @param driveCfg      SwerveDriveConfiguration for the swerve.
//    * @param controllerCfg Swerve Controller.
//    */
//   public SwerveSubsystem(SwerveDriveConfiguration driveCfg, SwerveControllerConfiguration controllerCfg)
//   {
//     swerveDrive = new SwerveDrive(driveCfg, controllerCfg, maximumSpeed);
//   }

//   /**
//    * The primary method for controlling the drivebase.  Takes a {@link Translation2d} and a rotation rate, and
//    * calculates and commands module states accordingly.  Can use either open-loop or closed-loop velocity control for
//    * the wheel velocities.  Also has field- and robot-relative modes, which affect how the translation vector is used.
//    *
//    * @param translation   {@link Translation2d} that is the commanded linear velocity of the robot, in meters per
//    *                      second. In robot-relative mode, positive x is torwards the bow (front) and positive y is
//    *                      torwards port (left).  In field-relative mode, positive x is away from the alliance wall
//    *                      (field North) and positive y is torwards the left wall when looking through the driver station
//    *                      glass (field West).
//    * @param rotation      Robot angular rate, in radians per second. CCW positive.  Unaffected by field/robot
//    *                      relativity.
//    * @param fieldRelative Drive mode.  True for field-relative, false for robot-relative.
//    */
//   public void drive(Translation2d translation, double rotation, boolean fieldRelative)
//   {
//     swerveDrive.drive(translation,
//                       rotation,
//                       fieldRelative,
//                       false); // Open loop is disabled since it shouldn't be used most of the time.
//   }

//   /**
//    * Drive the robot given a chassis field oriented velocity.
//    *
//    * @param velocity Velocity according to the field.
//    */
//   public void driveFieldOriented(ChassisSpeeds velocity)
//   {
//     swerveDrive.driveFieldOriented(velocity);
//   }

//   /**
//    * Drive according to the chassis robot oriented velocity.
//    *
//    * @param velocity Robot oriented {@link ChassisSpeeds}
//    */
//   public void drive(ChassisSpeeds velocity)
//   {
//     swerveDrive.drive(velocity);
//   }

//   @Override
//   public void periodic()
//   {
//   }

//   @Override
//   public void simulationPeriodic()
//   {
//   }

//   /**
//    * Get the swerve drive kinematics object.
//    *
//    * @return {@link SwerveDriveKinematics} of the swerve drive.
//    */
//   public SwerveDriveKinematics getKinematics()
//   {
//     return swerveDrive.kinematics;
//   }

//   /**
//    * Resets odometry to the given pose. Gyro angle and module positions do not need to be reset when calling this
//    * method.  However, if either gyro angle or module position is reset, this must be called in order for odometry to
//    * keep working.
//    *
//    * @param initialHolonomicPose The pose to set the odometry to
//    */
//   public void resetOdometry(Pose2d initialHolonomicPose)
//   {
//     swerveDrive.resetOdometry(initialHolonomicPose);
//   }

//   /**
//    * Gets the current pose (position and rotation) of the robot, as reported by odometry.
//    *
//    * @return The robot's pose
//    */
//   public Pose2d getPose()
//   {
//     return swerveDrive.getPose();
//   }

//   /**
//    * Set chassis speeds with closed-loop velocity control.
//    *
//    * @param chassisSpeeds Chassis Speeds to set.
//    */
//   public void setChassisSpeeds(ChassisSpeeds chassisSpeeds)
//   {
//     swerveDrive.setChassisSpeeds(chassisSpeeds);
//   }

//   /**
//    * Post the trajectory to the field.
//    *
//    * @param trajectory The trajectory to post.
//    */
//   public void postTrajectory(Trajectory trajectory)
//   {
//     swerveDrive.postTrajectory(trajectory);
//   }

//   /**
//    * Resets the gyro angle to zero and resets odometry to the same position, but facing toward 0.
//    */
//   public void zeroGyro()
//   {
//     swerveDrive.zeroGyro();
//   }

//   /**
//    * Sets the drive motors to brake/coast mode.
//    *
//    * @param brake True to set motors to brake mode, false for coast.
//    */
//   public void setMotorBrake(boolean brake)
//   {
//     swerveDrive.setMotorIdleMode(brake);
//   }

//   /**
//    * Gets the current yaw angle of the robot, as reported by the imu.  CCW positive, not wrapped.
//    *
//    * @return The yaw angle
//    */
//   public Rotation2d getHeading()
//   {
//     return swerveDrive.getYaw();
//   }

//   /**
//    * Get the chassis speeds based on controller input of 2 joysticks. One for speeds in which direction. The other for
//    * the angle of the robot.
//    *
//    * @param xInput   X joystick input for the robot to move in the X direction.
//    * @param yInput   Y joystick input for the robot to move in the Y direction.
//    * @param headingX X joystick which controls the angle of the robot.
//    * @param headingY Y joystick which controls the angle of the robot.
//    * @return {@link ChassisSpeeds} which can be sent to th Swerve Drive.
//    */
//   public ChassisSpeeds getTargetSpeeds(double xInput, double yInput, double headingX, double headingY)
//   {
//     xInput = Math.pow(xInput, 3);
//     yInput = Math.pow(yInput, 3);
//     return swerveDrive.swerveController.getTargetSpeeds(xInput,
//                                                         yInput,
//                                                         headingX,
//                                                         headingY,
//                                                         getHeading().getRadians(),
//                                                         maximumSpeed);
//   }

//   /**
//    * Get the chassis speeds based on controller input of 1 joystick and one angle. Control the robot at an offset of
//    * 90deg.
//    *
//    * @param xInput X joystick input for the robot to move in the X direction.
//    * @param yInput Y joystick input for the robot to move in the Y direction.
//    * @param angle  The angle in as a {@link Rotation2d}.
//    * @return {@link ChassisSpeeds} which can be sent to th Swerve Drive.
//    */
//   public ChassisSpeeds getTargetSpeeds(double xInput, double yInput, Rotation2d angle)
//   {
//     xInput = Math.pow(xInput, 3);
//     yInput = Math.pow(yInput, 3);
//     return swerveDrive.swerveController.getTargetSpeeds(xInput,
//                                                         yInput,
//                                                         angle.getRadians(),
//                                                         getHeading().getRadians(),
//                                                         maximumSpeed);
//   }

//   /**
//    * Gets the current field-relative velocity (x, y and omega) of the robot
//    *
//    * @return A ChassisSpeeds object of the current field-relative velocity
//    */
//   public ChassisSpeeds getFieldVelocity()
//   {
//     return swerveDrive.getFieldVelocity();
//   }

//   /**
//    * Gets the current velocity (x, y and omega) of the robot
//    *
//    * @return A {@link ChassisSpeeds} object of the current velocity
//    */
//   public ChassisSpeeds getRobotVelocity()
//   {
//     return swerveDrive.getRobotVelocity();
//   }

//   /**
//    * Get the {@link SwerveController} in the swerve drive.
//    *
//    * @return {@link SwerveController} from the {@link SwerveDrive}.
//    */
//   public SwerveController getSwerveController()
//   {
//     return swerveDrive.swerveController;
//   }

//   /**
//    * Get the {@link SwerveDriveConfiguration} object.
//    *
//    * @return The {@link SwerveDriveConfiguration} fpr the current drive.
//    */
//   public SwerveDriveConfiguration getSwerveDriveConfiguration()
//   {
//     return swerveDrive.swerveDriveConfiguration;
//   }

//   /**
//    * Lock the swerve drive to prevent it from moving.
//    */
//   public void lock()
//   {
//     swerveDrive.lockPose();
//   }

//   /**
//    * Gets the current pitch angle of the robot, as reported by the imu.
//    *
//    * @return The heading as a {@link Rotation2d} angle
//    */
//   public Rotation2d getPitch()
//   {
//     return swerveDrive.getPitch();
//   }

//   /**
//    * Add a fake vision reading for testing purposes.
//    */
//   public void addFakeVisionReading()
//   {
//     swerveDrive.addVisionMeasurement(new Pose2d(3, 3, Rotation2d.fromDegrees(65)), Timer.getFPGATimestamp());
//   }
//   /**
//    * Add vision measurement to swervedrive
//    * @param pose
//    * @param timestamp
//    */
//   /*public void addVisionMeasurement(Pose2d pose, double timestamp){//, double timeStampSeconds){
//     swerveDrive.addVisionMeasurement(pose, timestamp);
//   }*/
// } */