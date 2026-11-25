package jp.co.sss.lms.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
		//川﨑航 - Task26 Mapを設定
		attendanceForm.setHourMap(attendanceUtil.setHour());
		attendanceForm.setMinuteMap(attendanceUtil.setMinute());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));

			//川﨑航 - Task26 出勤時間
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());

			TrainingTime trainingStartTime = new TrainingTime(
					attendanceManagementDto.getTrainingStartTime());

			if (trainingStartTime.isNotBlank()) {
				dailyAttendanceForm.setTrainingStartTimeHour(trainingStartTime.getHour());
				dailyAttendanceForm.setTrainingStartTimeMinute(trainingStartTime.getMinute());
			}

			//川﨑航 - Task26 退勤時間
			dailyAttendanceForm
					.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());

			TrainingTime trainingEndTime = new TrainingTime(
					attendanceManagementDto.getTrainingEndTime());

			if (trainingEndTime.isNotBlank()) {
				dailyAttendanceForm.setTrainingEndTimeHour(trainingEndTime.getHour());
				dailyAttendanceForm.setTrainingEndTimeMinute(trainingEndTime.getMinute());
			}

			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 入力された出退勤の{時間}{分}をhh:mm形式に変換する処理
	 * 
	 * @author 川﨑 - Task26
	 * @param attendanceForm
	 */
	public void formatConversion(AttendanceForm attendanceForm) {
		//日時勤怠フォームのリスト分繰り返す
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {
			//出勤時間の変換
			if (dailyAttendanceForm.getTrainingStartTimeHour() != null
					&& dailyAttendanceForm.getTrainingStartTimeMinute() != null) {
				dailyAttendanceForm.setTrainingStartTime(String.format("%02d:%02d",
						dailyAttendanceForm.getTrainingStartTimeHour(),
						dailyAttendanceForm.getTrainingStartTimeMinute()));
			}

			//退勤時間の変換
			if (dailyAttendanceForm.getTrainingEndTimeHour() != null
					&& dailyAttendanceForm.getTrainingEndTimeMinute() != null) {
				dailyAttendanceForm.setTrainingEndTime(String.format("%02d:%02d",
						dailyAttendanceForm.getTrainingEndTimeHour(),
						dailyAttendanceForm.getTrainingEndTimeMinute()));
			}
		}
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 過去日の勤怠未入力チェック
	 * 
	 * @author 川﨑 - Task25
	 * @return 過去日に未入力の勤怠がある場合true
	 */
	public Boolean notEnterCheck() {

		//フォーマットパターンを設定
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/M/d");

		// 現在日付を取得
		Date date = new Date();

		// 日付のフォーマットして時刻部分を切り捨てる
		try {
			date = simpleDateFormat.parse(simpleDateFormat.format(date));
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}

		// 未入力件数を取得
		Integer count = tStudentAttendanceMapper.notEnterCount(
				loginUserDto.getLmsUserId(),
				Constants.DB_FLG_FALSE,
				date);

		// 未入力がある場合trueを返す
		return count > 0;
	}

	/**
	 * 勤怠入力チェック
	 * 
	 * @author 川﨑 - Task27
	 * @param attendanceForm
	 * @param result
	 * 
	 */
	public void updateInputCheck(AttendanceForm attendanceForm, BindingResult result) {

		//日時勤怠フォームの件数分繰り返す
		for (int i = 0; i < attendanceForm.getAttendanceList().size(); i++) {
			DailyAttendanceForm dailyAttendanceForm = attendanceForm.getAttendanceList().get(i);
			String fix = "attendanceList[" + i + "].";

			//備考の文字数チェック
			if (dailyAttendanceForm.getNote() != null
					&& dailyAttendanceForm.getNote().length() > 100) {
				result.rejectValue(fix + "note", "maxlength", new String[] { "備考", "100" }, null);
			}

			// 出勤時間の入力チェック
			Integer startHour = dailyAttendanceForm.getTrainingStartTimeHour();
			Integer startMinute = dailyAttendanceForm.getTrainingStartTimeMinute();

			if (startHour == null && startMinute != null) {

				// 時間が未入力の場合
				result.rejectValue(fix + "trainingStartTimeHour", "input.invalid", new String[] { "出勤時間" }, null);

			} else if (startHour != null && startMinute == null) {

				// 分が未入力の場合
				result.rejectValue(fix + "trainingStartTimeMinute", "input.invalid", new String[] { "出勤時間" }, null);
			}

			// 退勤時間の入力チェック
			Integer endHour = dailyAttendanceForm.getTrainingEndTimeHour();
			Integer endMinute = dailyAttendanceForm.getTrainingEndTimeMinute();

			if (endHour == null && endMinute != null) {

				// 時間が未入力の場合
				result.rejectValue(fix + "trainingEndTimeHour", "input.invalid", new String[] { "退勤時間" }, null);

			} else if (endHour != null && endMinute == null) {

				// 分が未入力の場合
				result.rejectValue(fix + "trainingEndTimeMinute", "input.invalid", new String[] { "退勤時間" }, null);
			}

			//出勤・退勤時刻をTrainingTimeに変換
			TrainingTime startTime = startHour != null && startMinute != null
					? new TrainingTime(startHour, startMinute)
					: new TrainingTime("");

			TrainingTime endTime = endHour != null && endMinute != null
					? new TrainingTime(endHour, endMinute)
					: new TrainingTime("");

			// 出勤時間が未入力で、退勤時間が入力されている場合
			if (startHour == null && startMinute == null && endTime.isNotBlank()) {
				result.rejectValue(fix + "trainingStartTimeHour", Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
				result.rejectValue(fix + "trainingStartTimeMinute", Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}

			//出勤・退勤時間が未入力で、中抜け時間が入力されている場合
			if (startTime.isBlank() && endTime.isBlank() && dailyAttendanceForm.getBlankTime() != null) {
				result.rejectValue(fix + "blankTime", "input.invalid", new String[] { "出勤時間", "退勤時間" }, null);
			}

			//出勤・退勤時間が両方入力されている場合
			if (startTime.isNotBlank() && endTime.isNotBlank()) {

				//出勤時間 ＞ 退勤時間かどうかチェック
				if (startTime.compareTo(endTime) > 0) {
				    result.rejectValue(fix + "trainingStartTimeHour", Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
				    result.rejectValue(fix + "trainingStartTimeMinute", Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
				    result.rejectValue(fix + "trainingEndTimeHour", Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
				    result.rejectValue(fix + "trainingEndTimeMinute", Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
				}

				//中抜け時間が勤務時間を超えていないかチェック
				if (startTime.compareTo(endTime) <= 0 && dailyAttendanceForm.getBlankTime() != null) {

					TrainingTime workTime = endTime.subtract(startTime);
					TrainingTime blankTime = attendanceUtil.calcBlankTime(dailyAttendanceForm.getBlankTime());

					if (blankTime.compareTo(workTime) > 0) {
						result.rejectValue(fix + "blankTime", "attendance.blankTimeError");
					}
				}
			}
		}
	}
}
