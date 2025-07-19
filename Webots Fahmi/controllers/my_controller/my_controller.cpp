#include <webots/Robot.hpp>
#include <webots/Motor.hpp>
#include <webots/Camera.hpp>
#include <iostream>
#include <chrono>

#define TIME_STEP 64
#define SEARCH_DURATION 0.7
#define REVERSE_TIME 1.2

using namespace webots;
using namespace std::chrono;

Robot robot;
Motor *lm = robot.getMotor("left wheel");
Motor *rm = robot.getMotor("right wheel");

Camera *leftCam = robot.getCamera("leftColor");
Camera *frontCam = robot.getCamera("frontColor");
Camera *rightCam = robot.getCamera("rightColor");

double MAX_SPEED;

// Variabel memori
std::string lastDirection = "left";
double searchingStart = -1;
bool reverseStarted = false;
double reverseStartTime = 0;

// Fungsi untuk mendapatkan waktu sekarang dalam detik
double now() {
  return duration_cast<duration<double>>(steady_clock::now().time_since_epoch()).count();
}

// Fungsi deteksi warna hitam dari kamera
bool isBlack(Camera *cam) {
  const unsigned char *image = cam->getImage();
  int width = cam->getWidth();
  int height = cam->getHeight();

  int x = width / 2;
  int y = height / 2;

  int r = Camera::imageGetRed(image, width, x, y);
  int g = Camera::imageGetGreen(image, width, x, y);
  int b = Camera::imageGetBlue(image, width, x, y);

  double brightness = (r + g + b) / 3.0;
  return brightness < 70;
}

int main() {
  // Setup motor
  lm->setPosition(INFINITY);
  rm->setPosition(INFINITY);
  MAX_SPEED = lm->getMaxVelocity();

  // Enable kamera
  leftCam->enable(TIME_STEP);
  frontCam->enable(TIME_STEP);
  rightCam->enable(TIME_STEP);

  while (robot.step(TIME_STEP) != -1) {
    bool leftBlack = isBlack(leftCam);
    bool frontBlack = isBlack(frontCam);
    bool rightBlack = isBlack(rightCam);

    if (!leftBlack && !frontBlack && !rightBlack) {
      if (searchingStart < 0) {
        searchingStart = now();
        reverseStarted = false;
        std::cout << "⚪ Garis hilang, mulai mencari" << std::endl;
      }

      double elapsed = now() - searchingStart;

      if (elapsed < SEARCH_DURATION) {
        if (lastDirection == "left") {
          lm->setVelocity(-0.4 * MAX_SPEED);
          rm->setVelocity(0.4 * MAX_SPEED);
          std::cout << "🔄 Mencari kiri" << std::endl;
        } else {
          lm->setVelocity(0.4 * MAX_SPEED);
          rm->setVelocity(-0.4 * MAX_SPEED);
          std::cout << "🔄 Mencari kanan" << std::endl;
        }
      } else if (!reverseStarted) {
        reverseStartTime = now();
        reverseStarted = true;
        std::cout << "🔁 Tidak ditemukan, putar balik" << std::endl;
      } else if (now() - reverseStartTime < REVERSE_TIME) {
        lm->setVelocity(0.8 * MAX_SPEED);
        rm->setVelocity(-0.8 * MAX_SPEED);
        std::cout << "🔁 Putar 180°" << std::endl;
      } else {
        searchingStart = -1;
        reverseStarted = false;
        std::cout << "🔄 Coba cari ulang" << std::endl;
      }

      continue;
    } else {
      searchingStart = -1;
      reverseStarted = false;
    }

    if (frontBlack) {
      lm->setVelocity(1.0 * MAX_SPEED);
      rm->setVelocity(1.0 * MAX_SPEED);
      std::cout << "⬆️ Maju" << std::endl;
    } else if (leftBlack) {
      lm->setVelocity(0.3 * MAX_SPEED);
      rm->setVelocity(1.0 * MAX_SPEED);
      lastDirection = "left";
      std::cout << "↩️ Belok kiri" << std::endl;
    } else if (rightBlack) {
      lm->setVelocity(1.0 * MAX_SPEED);
      rm->setVelocity(0.3 * MAX_SPEED);
      lastDirection = "right";
      std::cout << "↪️ Belok kanan" << std::endl;
    }
  }

  return 0;
}
