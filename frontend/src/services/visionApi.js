// src/services/visionApi.js
import axios from "axios";

/**
 * 방 이미지 AI 분석 요청
 * @param {Array} rooms - zustand rooms 상태
 */
export async function analyzeVision(rooms) {
  const formData = new FormData();

  rooms.forEach((room) => {
    const file = room.images?.[0]?.file;
    if (file) formData.append("files", file);
  });

  const roomsPayload = rooms.map((room, index) => ({
    room_type: mapRoomType(room.type, index),
    file_index: index,
  }));

  formData.append("rooms", JSON.stringify(roomsPayload));

  // Vite proxy를 전제로 상대경로 호출
  const response = await axios.post("/api/vision/", formData);

  return response.data;
}

function mapRoomType(type, index) {
  switch (type) {
    case "거실":
      return "LIVING";
    case "주방":
      return "KITCHEN";
    case "베란다":
      return "BALCONY";
    case "다용도실":
      return "UTILITY";
    case "방":
      return `ROOM${index + 1}`;
    default:
      return "UNKNOWN";
  }
}
