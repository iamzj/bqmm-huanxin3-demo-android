package com.easemob.easeui.widget.chatrow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.bumptech.glide.Glide;
import com.easemob.chat.EMChatManager;
import com.easemob.chat.EMMessage;
import com.easemob.chat.EMMessage.ChatType;
import com.easemob.chat.TextMessageBody;
import com.easemob.easeui.R;
import com.easemob.easeui.ui.EaseChatFragment;
import com.easemob.easeui.utils.EaseSmileUtils;
import com.easemob.exceptions.EaseMobException;
import com.melink.baseframe.bitmap.BitmapCreate;
import com.melink.baseframe.utils.DensityUtils;
import com.melink.baseframe.utils.StringUtils;
import com.melink.bqmmsdk.bean.Emoji;
import com.melink.bqmmsdk.sdk.BQMM;
import com.melink.bqmmsdk.sdk.IFetchEmojisByCodeListCallback;
import com.melink.bqmmsdk.ui.store.EmojiDetail;
import com.melink.bqmmsdk.widget.GifMovieView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EaseChatRowText extends EaseChatRow{

    private TextView contentView;
    private GifMovieView emojiView;

    public EaseChatRowText(Context context, EMMessage message, int position, BaseAdapter adapter) {
		super(context, message, position, adapter);
	}

	@Override
	protected void onInflatView() {
		inflater.inflate(message.direct == EMMessage.Direct.RECEIVE ?
				R.layout.ease_row_received_message : R.layout.ease_row_sent_message, this);
	}

	@Override
	protected void onFindViewById() {
		contentView = (TextView) findViewById(R.id.tv_chatcontent);
        emojiView =(GifMovieView)findViewById(R.id.tv_sendGif);
        /**
         * emojiView的OnClickListener会让聊天气泡的长按事件失效，所以要在这里设置一个OnLongClickListener，让它调用bubbleLayout的长按事件
         */
        emojiView.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                bubbleLayout.performLongClick();
                return true;
            }
        });
    }

    @Override
    public void onSetUpView() {
        setPic();
        handleTextMessage();
    }

    protected void handleTextMessage() {
        if (message.direct == EMMessage.Direct.SEND) {
            setMessageSendCallback();
            switch (message.status) {
            case CREATE:
                progressBar.setVisibility(View.VISIBLE);
                statusView.setVisibility(View.GONE);
                // 发送消息
//                sendMsgInBackground(message);
                break;
            case SUCCESS: // 发送成功
                progressBar.setVisibility(View.GONE);
                statusView.setVisibility(View.GONE);
                break;
            case FAIL: // 发送失败
                progressBar.setVisibility(View.GONE);
                statusView.setVisibility(View.VISIBLE);
                break;
            case INPROGRESS: // 发送中
                progressBar.setVisibility(View.VISIBLE);
                statusView.setVisibility(View.GONE);
                break;
            default:
               break;
            }
        }else{
            if(!message.isAcked() && message.getChatType() == ChatType.Chat){
                try {
                    EMChatManager.getInstance().ackMessageRead(message.getFrom(), message.getMsgId());
                    message.isAcked = true;
                } catch (EaseMobException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onUpdateView() {
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onBubbleClick() {
        // TODO Auto-generated method stub

    }

    /**
     * 对存储在Json中的消息进行解析
     *
     * @param msg_Data
     * @return 消息的文本表达
     */
    public static String parseMsgData(JSONArray msg_Data) {
        StringBuilder sendMsg = new StringBuilder();
        try {
            for (int i = 0; i < msg_Data.length(); i++) {
                JSONArray childArray = msg_Data.getJSONArray(i);
                if (childArray.get(1).equals("1")) {
                    sendMsg.append("[").append(childArray.get(0)).append("]");
                } else {
                    sendMsg.append(childArray.get(0));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return sendMsg.toString();
    }

    private void setPic() {
        // 判断是否是表情文本
        String msgType;
        String dataStr;
        String msg;
        JSONObject msgBody;
        try {
            //这里先按照新的消息格式进行解析。在新的消息格式中，表情的全部信息被放在一个Json字符串中。
            msg = message.getStringAttribute("mm_ext");
            msgBody = new JSONObject(msg);
            msgType = msgBody.getString("txt_msgType");
            dataStr = parseMsgData(msgBody.getJSONArray("msg_data"));
        } catch (EaseMobException | JSONException e) {
            //如果按照新的消息格式无法解析，则按照老格式进行解析。在老格式中，表情的类型和内容数据被用两个key分别存储
            try {
                msgType = message.getStringAttribute("txt_msgType");
                dataStr = parseMsgData(message.getJSONArrayAttribute("msg_data"));
            } catch (EaseMobException e1) {
                msgType = "";
                dataStr = "";
            }
        }
        switch (msgType) {
            case EaseChatFragment.FACETYPE:
                contentView.setVisibility(View.GONE);
                contentView.setClickable(false);
                // 展示默认图片
                Glide.with(activity).load(R.drawable.ease_default_expression).into(emojiView);

                List<String> codeList = new ArrayList<String>();
                codeList.add(dataStr.replace("[", "").replace("]", ""));
                BQMM.getInstance().fetchBigEmojiByCodeList(activity, codeList, new IFetchEmojisByCodeListCallback() {
                    @Override
                    public void onSuccess(List<Emoji> emojis) {
                        // 错误的表情Code
                        if (emojis == null || emojis.size() <= 0) {
                            // 展示下载失败的默认图片
                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    Glide.with(activity).load(R.drawable.ease_default_expression).into(emojiView);
                                }
                            });
                            return;
                        }
                        final Emoji emoji = emojis.get(0);
                        // 错误的表情Code
                        if (emoji.getGuid() == null || emoji.getGuid().equals("")) {
                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    Glide.with(activity).load(R.drawable.ease_default_expression).into(emojiView);
                                }
                            });
                            return;
                        }
                        activity.runOnUiThread(new Runnable() {
                            public void run() {
                                emojiView.setClickable(true);
                                // gif则按照gif展示，否则展示图片
                                emojiView.setVisibility(View.VISIBLE);
                                if (emoji.getMainImage().endsWith(".png")) {
                                    emojiView.setMovie(null);
                                    Glide.with(activity).load(emoji.getMainImage()).placeholder(R.drawable.ease_default_expression).into(emojiView);
                                } else if (emoji.getMainImage().endsWith(".gif")) {
                                    emojiView.setVisibility(View.VISIBLE);
                                    if (emoji.getPathofImage() == null || emoji.getPathofImage().equals("")) {
                                        emojiView.setResource(StringUtils.decodestr(emoji.getMainImage()));// 读网络上的
                                    } else {
                                        emojiView.setMovieResourceByUri(emoji.getPathofImage());
                                    }
                                }
                                // 添加点击事件，跳转只详情预览,如果item的emoji还未请求下来，则跳转失效
                                emojiView.setOnClickListener(new OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        if (emoji.getPackageId() == null || emoji.getPackageId().equals("")) {
                                            return;
                                        }
                                        Intent it = new Intent(activity, EmojiDetail.class);
                                        Bundle bundle = new Bundle();
                                        bundle.putSerializable("Emoji_Detail", emoji);
                                        it.putExtras(bundle);
                                        activity.startActivity(it);
                                    }
                                });
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable arg0) {

                    }
                });

                break;
            case EaseChatFragment.EMOJITYPE:
                contentView.setVisibility(View.VISIBLE);
                emojiView.setVisibility(View.GONE);
                // 表情如果未下载，则下载表情进行展示
                contentView.setText("");
                showTextInfoFromStr(contentView, dataStr, position);
                break;
            default:
                contentView.setVisibility(View.VISIBLE);
                emojiView.setVisibility(View.GONE);
                TextMessageBody txtBody = (TextMessageBody) message.getBody();
                Spannable span = EaseSmileUtils.getSmiledText(context, txtBody.getMessage());
                // 设置内容
                contentView.setText(span, BufferType.SPANNABLE);
                break;
        }
    }
    private void showTextInfoFromStr(final TextView tv_chatcontent,
                                     final String messagecontent, final int position) {
        if (!(findEmojiByMsgStr(messagecontent).size() > 0)) {
            tv_chatcontent.setText(messagecontent);
            return;
        }
        BQMM.getInstance().fetchSmallEmojiByCodeList(context,
                findEmojiByMsgStr(messagecontent),
                new IFetchEmojisByCodeListCallback() {
                    @Override
                    public void onSuccess(final List<Emoji> emojis) {
                        ((Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (emojis == null) {
                                    tv_chatcontent.setText(messagecontent);
                                    return;
                                }
                                showTextInfo(tv_chatcontent,
                                        BQMM.getInstance().getMessageParser()
                                                .parse(messagecontent, emojis));
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable arg0) {

                    }
                });
    }
    private List<String> findEmojiByMsgStr(String messageStr) {
        List<String> emoji_list = new ArrayList<String>();
        Pattern pattern1 = Pattern.compile("\\[([^\\[\\]]+)\\]",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher1 = pattern1.matcher(messageStr);
        while (matcher1.find()) {
            emoji_list.add(matcher1.group(1));
        }
        return emoji_list;
    }

    private void showTextInfo(final TextView tv_chatcontent, List<Object> emojis) {
        // 根据返回的list集合实现图文混排
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < emojis.size(); i++) {
            if (emojis.get(i).getClass().equals(Emoji.class)) {
                Emoji item = (Emoji) emojis.get(i);
                String tempText = "[" + item.getEmoCode() + "]";
                sb.append(tempText);
                // 此处需要判断，如果是非法Code，item的guid为空
                if (item.getGuid() != null && !item.getGuid().equals("null")) {
                    try {
                        Bitmap bit = BitmapCreate.bitmapFromFile(item.getPathofThumb(), DensityUtils.dip2px(activity, 30), DensityUtils.dip2px(activity, 30));
                        sb.setSpan(new ImageSpan(activity, bit), sb.length() - tempText.length(), sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                sb.append(emojis.get(i).toString());
            }
        }
        tv_chatcontent.setText(sb);
    }
}
